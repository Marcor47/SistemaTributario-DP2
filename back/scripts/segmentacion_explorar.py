# /// script
# requires-python = ">=3.10"
# dependencies = ["psycopg[binary]>=3.2", "numpy>=1.26", "pandas>=2.2", "scikit-learn>=1.5"]
# ///
"""Exploración de la segmentación de contribuyentes (RF-043, paso 1). Solo lee el oro; no escribe nada.

Calcula por contribuyente (seudónimo) las variables de comportamiento de pago del último lote publicado, prueba
K-means con k = 2..8 sobre las variables transformadas (logaritmo en las asimétricas) y estandarizadas, y deja un JSON con todo lo
necesario para los gráficos: métricas por k, estabilidad, perfiles de cada segmento, proyección PCA, contraste con
HDBSCAN y la historia de pago por año de cada segmento.

    uv run back/scripts/segmentacion_explorar.py --dsn postgresql://municipalidad:municipalidad@localhost:5432/tributario \
        --salida segmentacion.json [--validar-perfiles-demo]

--validar-perfiles-demo compara los segmentos con los perfiles de pago que la semilla local asignó a cada
contribuyente ficticio (R__zz_demo_04_historia_san_miguel.sql). Es solo una prueba: el modelo nunca los usa.
"""
import argparse
import hashlib
import hmac
import json
from datetime import date

import numpy as np
import pandas as pd
import psycopg
from sklearn.cluster import HDBSCAN, KMeans
from sklearn.decomposition import PCA
from sklearn.metrics import adjusted_rand_score, davies_bouldin_score, silhouette_samples, silhouette_score
from sklearn.preprocessing import QuantileTransformer, RobustScaler, StandardScaler

VENTANA_ANIOS = 3
SEMILLA = 20261111

# Variables del modelo: solo comportamiento de pago, con su nombre para la jefatura y su unidad.
VARIABLES = {
    "pct_a_tiempo": ("Cuotas pagadas a tiempo", "%"),
    "dias_atraso": ("Atraso medio al pagar", "días"),
    "ratio_deuda": ("Deuda vencida sobre lo emitido", "%"),
    "antiguedad_deuda": ("Antigüedad de la deuda", "años"),
    "meses_sin_pago": ("Meses desde el último pago", "meses"),
}
# Tamaño y composición: se describen en cada segmento pero no lo definen (ver la comparación de variantes).
DESCRIPTIVAS = {"num_predios": ("Predios", "n"), "pct_predial": ("Peso del Impuesto Predial", "%"),
                "pct_en_linea": ("Pagos en línea", "%")}
ESTRUCTURALES = ["log_predios", "pct_predial"]

SQL_CUOTAS = """
SELECT h.seudonimo_contribuyente AS c, h.id_predio, h.tributo, h.anio, h.fecha_vencimiento AS venc,
       h.monto_emitido::float AS emitido, h.monto_insoluto::float AS insoluto, h.estado_pago AS estado,
       h.fecha_pago
FROM analitica.hecho_cobranza h WHERE h.id_lote = %s
"""
SQL_PAGOS = """
SELECT seudonimo_contribuyente AS c, fecha_pago, canal FROM analitica.hecho_pago WHERE id_lote = %s
"""

# Perfiles que la semilla local asignó (solo para validar; ver --validar-perfiles-demo).
PERFILES_DEMO = {
    **{c: "PUNTUAL" for c in "110101 110104 110201 110202 110301 110401 110402 110501 058934 061177 080245".split()},
    **{c: "ANUAL" for c in "110102 110204 110506 014502 033218".split()},
    **{c: "ATRASADO" for c in "110103 110203 110205 110302 110303 110403 110502".split()},
    **{c: "RECUPERA" for c in "110105 110206 110304 110305 110404 110505".split()},
    **{c: "MOROSO" for c in "110106 110207 110306 110405 110503".split()},
    **{c: "PARCIAL" for c in "110307 110406 110504".split()},
    **{c: "ATRASADO_LEVE" for c in "045067 021390".split()},
}


def variables(cuotas: pd.DataFrame, pagos: pd.DataFrame, corte: date) -> pd.DataFrame:
    corte = pd.Timestamp(corte)
    q = cuotas.assign(venc=pd.to_datetime(cuotas.venc), fecha_pago=pd.to_datetime(cuotas.fecha_pago))
    vencidas = q[q.venc <= corte]
    pagada = vencidas.estado.eq("PAGADA")
    a_tiempo = pagada & (vencidas.fecha_pago <= vencidas.venc)
    atraso = (vencidas.fecha_pago.where(pagada, corte) - vencidas.venc).dt.days
    vencidas = vencidas.assign(a_tiempo=a_tiempo, atraso=atraso, impaga=~pagada)
    ventana = vencidas[vencidas.venc > corte - pd.DateOffset(years=VENTANA_ANIOS)]

    g = ventana.groupby("c")
    df = pd.DataFrame({
        "pct_a_tiempo": g.a_tiempo.mean() * 100,
        "dias_atraso": g.atraso.mean(),
    })
    gv = vencidas.groupby("c")
    impagas = vencidas[vencidas.impaga]
    df["ratio_deuda"] = (impagas.groupby("c").insoluto.sum() / gv.emitido.sum() * 100).reindex(df.index).fillna(0)
    df["antiguedad_deuda"] = ((corte - impagas.groupby("c").venc.min()).dt.days / 365.25).reindex(df.index).fillna(0)
    p = pagos.assign(fecha_pago=pd.to_datetime(pagos.fecha_pago))
    ultimo = p.groupby("c").fecha_pago.max()
    df["meses_sin_pago"] = ((corte - ultimo).dt.days / 30.44).reindex(df.index).fillna(120)
    df["num_predios"] = q.groupby("c").id_predio.nunique().reindex(df.index)
    df["log_predios"] = np.log1p(df.num_predios)
    emitido = q.groupby(["c", "tributo"]).emitido.sum().unstack(fill_value=0)
    df["pct_predial"] = (emitido.get("IMPUESTO_PREDIAL", 0) / emitido.sum(axis=1) * 100).reindex(df.index)
    df["pct_en_linea"] = (p.canal.eq("EN_LINEA").groupby(p.c).mean() * 100).reindex(df.index).fillna(0)
    return df


def historia_anual(cuotas: pd.DataFrame, corte: date) -> pd.DataFrame:
    """% de cuotas pagadas a tiempo por contribuyente y año de vencimiento."""
    q = cuotas.assign(venc=pd.to_datetime(cuotas.venc), fecha_pago=pd.to_datetime(cuotas.fecha_pago))
    q = q[q.venc <= pd.Timestamp(corte)]
    q = q.assign(a_tiempo=q.estado.eq("PAGADA") & (q.fecha_pago <= q.venc), anio=q.venc.dt.year)
    return q.groupby(["c", "anio"]).a_tiempo.mean().mul(100).rename("pct").reset_index()


def transformar(df: pd.DataFrame) -> pd.DataFrame:
    """Logaritmo en las variables con cola larga; el atraso conserva el signo (pagar adelantado es negativo)."""
    t = df.copy()
    if "dias_atraso" in t:
        t["dias_atraso"] = np.sign(t.dias_atraso) * np.log1p(t.dias_atraso.abs())
    for c in ("ratio_deuda", "antiguedad_deuda", "meses_sin_pago"):
        if c in t:
            t[c] = np.log1p(t[c])
    return t


def variantes(df: pd.DataFrame, perfiles) -> list:
    """Compara escalados y conjuntos de variables: el k de mejor silueta de cada uno."""
    escalados = {
        "Mediana e IQR": (lambda t: t, RobustScaler),
        "Estandarizado": (lambda t: t, StandardScaler),
        "Rangos (cuantiles)": (lambda t: t, lambda: QuantileTransformer(n_quantiles=min(len(df), 100))),
        "Log + estandarizado": (transformar, StandardScaler),
    }
    conjuntos = {"Pago (5)": list(VARIABLES), "Pago + estructura (7)": list(VARIABLES) + ESTRUCTURALES}
    filas = []
    for nombre_c, cols in conjuntos.items():
        for nombre_e, (tf, esc) in escalados.items():
            X = esc().fit_transform(tf(df[cols]))
            mejor = None
            for k in range(2, 9):
                l = KMeans(k, n_init=20, random_state=SEMILLA).fit_predict(X)
                s = silhouette_score(X, l)
                if mejor is None or s > mejor["silueta"]:
                    mejor = {"variables": nombre_c, "escalado": nombre_e, "k": k, "silueta": s}
                    if perfiles is not None:
                        mejor["ari_perfiles"] = adjusted_rand_score(perfiles, l)
            filas.append(mejor)
    return filas


def estabilidad(X: np.ndarray, k: int, rondas: int = 30) -> float:
    """ARI medio entre el modelo completo y modelos ajustados con el 80 % de los contribuyentes."""
    rng = np.random.default_rng(SEMILLA)
    base = KMeans(k, n_init=20, random_state=SEMILLA).fit_predict(X)
    aris = []
    for _ in range(rondas):
        idx = rng.choice(len(X), size=int(0.8 * len(X)), replace=False)
        m = KMeans(k, n_init=20, random_state=int(rng.integers(1 << 30))).fit(X[idx])
        aris.append(adjusted_rand_score(base, m.predict(X)))
    return float(np.mean(aris)), float(np.percentile(aris, 10))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dsn", required=True)
    ap.add_argument("--salida", required=True)
    ap.add_argument("--k", type=int, help="k a describir; por defecto el de mejor silueta")
    ap.add_argument("--validar-perfiles-demo", action="store_true")
    ap.add_argument("--clave-demo", default="samnet-demo-contribuyente")
    a = ap.parse_args()

    with psycopg.connect(a.dsn) as cn:
        cn.read_only = True
        id_lote, corte = cn.execute(
            "SELECT id_lote, fecha_corte FROM analitica.lote_etl WHERE estado = 'PUBLICADO' "
            "AND fuente = 'ps_etl_cobranza' ORDER BY fecha_corte DESC, id_lote DESC LIMIT 1").fetchone()
        cuotas = pd.DataFrame(cn.execute(SQL_CUOTAS, (id_lote,)).fetchall(),
                              columns=["c", "id_predio", "tributo", "anio", "venc", "emitido", "insoluto", "estado", "fecha_pago"])
        pagos = pd.DataFrame(cn.execute(SQL_PAGOS, (id_lote,)).fetchall(), columns=["c", "fecha_pago", "canal"])
        tipo = dict(cn.execute("SELECT seudonimo_contribuyente, tipo_persona FROM analitica.dim_contribuyente"))

    df = variables(cuotas, pagos, corte).sort_index()
    cols = list(VARIABLES)
    X = StandardScaler().fit_transform(transformar(df[cols]))

    perfiles = None
    if a.validar_perfiles_demo:
        seud = {hmac.new(a.clave_demo.encode(), c.encode(), hashlib.sha256).hexdigest(): p
                for c, p in PERFILES_DEMO.items()}
        perfiles = df.index.map(lambda s: seud.get(s, "SIN_PERFIL"))

    comparacion = variantes(df, perfiles)
    barrido = []
    for k in range(2, 9):
        km = KMeans(k, n_init=20, random_state=SEMILLA).fit(X)
        est, est_p10 = estabilidad(X, k)
        fila = {"k": k, "inercia": km.inertia_, "silueta": silhouette_score(X, km.labels_),
                "davies_bouldin": davies_bouldin_score(X, km.labels_), "estabilidad": est,
                "estabilidad_p10": est_p10, "tamano_min": int(np.bincount(km.labels_).min())}
        if perfiles is not None:
            fila["ari_perfiles"] = adjusted_rand_score(perfiles, km.labels_)
        barrido.append(fila)
    k = a.k or max(barrido, key=lambda r: r["silueta"])["k"]

    km = KMeans(k, n_init=20, random_state=SEMILLA).fit(X)
    # Numera los segmentos de mejor a peor pagador para que el orden se lea solo.
    orden = np.argsort(-df.groupby(km.labels_).pct_a_tiempo.mean().to_numpy())
    remap = {int(old): new for new, old in enumerate(orden)}
    df["segmento"] = [remap[int(l)] for l in km.labels_]
    centros_esc = km.cluster_centers_[orden]

    sil = silhouette_samples(X, df.segmento)
    pca = PCA(2, random_state=SEMILLA).fit(X)
    xy = pca.transform(X)
    hdb = HDBSCAN(min_cluster_size=3).fit(X)
    dist = np.linalg.norm(X - centros_esc[df.segmento.to_numpy()], axis=1)

    puntos = []
    for i, s in enumerate(df.index):
        p = {"seg": int(df.segmento.iloc[i]), "x": float(xy[i, 0]), "y": float(xy[i, 1]),
             "silueta": float(sil[i]), "hdbscan": int(hdb.labels_[i]), "dist": float(dist[i]),
             "tipo": tipo.get(s, "?"), **{c: float(df[c].iloc[i]) for c in cols + list(DESCRIPTIVAS)}}
        if perfiles is not None:
            p["perfil"] = perfiles[i]
        puntos.append(p)

    hist = historia_anual(cuotas, corte).merge(df.segmento, left_on="c", right_index=True)
    historia = (hist.groupby(["segmento", "anio"]).pct.agg(["mean", "count"]).reset_index()
                .rename(columns={"mean": "pct", "count": "n"}).to_dict("records"))

    resumen = []
    for s, grupo in df.groupby("segmento"):
        resumen.append({"seg": int(s), "n": len(grupo),
                        "mediana": {c: float(grupo[c].median()) for c in cols + list(DESCRIPTIVAS)},
                        "centro_escalado": dict(zip(cols, map(float, centros_esc[s]))),
                        "juridicas": int(sum(tipo.get(x) == "JURIDICA" for x in grupo.index))})

    salida = {
        "lote": id_lote, "fecha_corte": str(corte), "n": len(df), "k": k, "ventana_anios": VENTANA_ANIOS,
        "variables": {c: {"nombre": n, "unidad": u} for c, (n, u) in {**VARIABLES, **DESCRIPTIVAS}.items()},
        "modelo": cols, "barrido": barrido, "variantes": comparacion, "segmentos": resumen, "puntos": puntos, "historia": historia,
        "pca_varianza": list(map(float, pca.explained_variance_ratio_)),
        "pca_cargas": {c: list(map(float, pca.components_[:, j])) for j, c in enumerate(cols)},
        "correlacion": df[cols].corr(method="spearman").round(3).to_dict(),
        "hdbscan": {"grupos": int(hdb.labels_.max() + 1), "ruido": int((hdb.labels_ < 0).sum()),
                    "ari_kmeans": adjusted_rand_score(hdb.labels_, df.segmento)},
    }
    if perfiles is not None:
        salida["cruce"] = pd.crosstab(perfiles, df.segmento.to_numpy()).to_dict("index")
        salida["ari_perfiles"] = adjusted_rand_score(perfiles, df.segmento)
    with open(a.salida, "w", encoding="utf-8") as f:
        json.dump(salida, f, ensure_ascii=False, indent=1, default=float)

    print(f"lote {id_lote} (corte {corte}): {len(df)} contribuyentes, k elegido = {k}")
    print(pd.DataFrame(comparacion).round(3).to_string(index=False))
    print(pd.DataFrame(barrido).round(3).to_string(index=False))
    print(df.groupby("segmento")[cols + list(DESCRIPTIVAS)].median().round(1).to_string())
    if perfiles is not None:
        print(pd.crosstab(perfiles, df.segmento.to_numpy()).to_string())
        print("ARI con los perfiles de la semilla:", round(salida["ari_perfiles"], 3))
    print("HDBSCAN:", salida["hdbscan"])


if __name__ == "__main__":
    main()
