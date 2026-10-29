import * as DocumentPicker from 'expo-document-picker'
import * as ImagePicker from 'expo-image-picker'
import { TAMANO_MAXIMO_BYTES, TIPOS_PERMITIDOS } from './solicitudPredioService'

const POR_EXTENSION = { pdf: 'application/pdf', jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png' }
const EXTENSION = { 'application/pdf': 'pdf', 'image/jpeg': 'jpg', 'image/png': 'png' }

// Las fotos se piden en el formato más compatible (JPEG) para que iOS no entregue HEIC.
const OPCIONES_FOTO = { mediaTypes: 'images', quality: 0.8, preferredAssetRepresentationMode: 'compatible' }

export function formatearTamano(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`
}

function normalizar(asset, prefijo) {
  const nombreOriginal = asset.name || asset.fileName || ''
  const extension = nombreOriginal.split('.').pop()?.toLowerCase()
  const tipo = (asset.mimeType || POR_EXTENSION[extension] || '').toLowerCase()
  const nombre = nombreOriginal || `${prefijo}-${Date.now()}.${EXTENSION[tipo] || 'jpg'}`
  return { uri: asset.uri, nombre, tipo, tamano: asset.size ?? asset.fileSize ?? asset.file?.size ?? null, file: asset.file }
}

/** Valida en el celular lo mismo que valida el backend (tipo y 10 MB) para avisar antes de enviar. */
export function validarArchivo(archivo) {
  if (!TIPOS_PERMITIDOS.includes(archivo.tipo)) {
    return 'Solo se aceptan archivos PDF, JPG o PNG. Si es una foto, tómala con la cámara de la app.'
  }
  if (archivo.tamano === 0) return 'El archivo está vacío. Elige otro.'
  if (archivo.tamano != null && archivo.tamano > TAMANO_MAXIMO_BYTES) {
    return `El archivo pesa ${formatearTamano(archivo.tamano)}; el máximo permitido es 10 MB.`
  }
  return null
}

/**
 * Abre el origen elegido y devuelve { archivo } , { error } o null si el usuario canceló.
 * @param origen 'camara' | 'galeria' | 'archivo'
 */
export async function seleccionarArchivo(origen) {
  let resultado
  if (origen === 'camara') {
    const permiso = await ImagePicker.requestCameraPermissionsAsync()
    if (!permiso.granted) {
      return { error: 'Necesitamos permiso para usar la cámara. Actívalo en los ajustes de tu celular.' }
    }
    resultado = await ImagePicker.launchCameraAsync(OPCIONES_FOTO)
  } else if (origen === 'galeria') {
    resultado = await ImagePicker.launchImageLibraryAsync(OPCIONES_FOTO)
  } else {
    resultado = await DocumentPicker.getDocumentAsync({ type: TIPOS_PERMITIDOS, copyToCacheDirectory: true })
  }
  if (resultado.canceled || !resultado.assets?.length) return null
  const archivo = normalizar(resultado.assets[0], origen === 'archivo' ? 'documento' : 'foto')
  const error = validarArchivo(archivo)
  return error ? { error } : { archivo }
}
