/** A problem with the chosen file itself, with a sentence that's safe to show. */
export class PhotoError extends Error {}

/** Longest side sent to the server, which shrinks it again to what it stores. */
const MAX_SIDE = 1600

/**
 * Makes a photo small before it's uploaded. A phone photo is 3 to 8 MB; this
 * sends about a tenth of that, which matters on mobile data. Drawing it again
 * also turns it the right way up and leaves behind what the file carried
 * besides the picture (where it was taken, the phone model).
 *
 * The server does not rely on any of this: it checks and shrinks every upload itself.
 */
export async function shrinkPhoto(file: File): Promise<Blob> {
  const url = URL.createObjectURL(file)
  try {
    const image = await decode(url)
    const scale = Math.min(1, MAX_SIDE / Math.max(image.naturalWidth, image.naturalHeight))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
    canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
    const context = canvas.getContext('2d')
    if (!context) return file
    context.fillStyle = '#fff'   // JPEG has no see-through, so a cut-out PNG gets a white background
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, 'image/jpeg', 0.86))
    return blob ?? file
  } finally {
    URL.revokeObjectURL(url)
  }
}

function decode(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new PhotoError('This browser can’t open that file. Choose a JPG or PNG photo.'))
    image.src = url
  })
}
