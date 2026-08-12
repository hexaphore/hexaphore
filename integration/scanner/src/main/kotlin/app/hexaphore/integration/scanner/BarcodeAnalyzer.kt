package app.hexaphore.integration.scanner

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import app.hexaphore.domain.food.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.common.Barcode as MlBarcode

/**
 * Le décodeur branché sur le flux de la caméra.
 *
 * **Trois formats déclarés, et pas un de plus.** Restreindre la liste n'est pas une
 * économie : ML Kit rendrait volontiers un QR code ou un code de rayonnage, et un
 * faux positif y ressemble à un scan réussi. Ce sont les symbologies des produits
 * alimentaires, et rien d'autre ([docs/02][parcours]).
 *
 * **UPC-E n'y figure pas**, et c'est la conséquence directe de [D63][decisions] : huit
 * chiffres ne disent pas s'ils sont un EAN-8 ou un UPC-E compressé. Le déclarer ici
 * ferait remonter des codes que [Barcode] lirait comme des EAN-8, donc désignant un
 * autre produit. Refuser la symbologie à la source est plus honnête que de la
 * rattraper par la clé de contrôle une fois sur dix.
 *
 * **La règle est ailleurs.** Tout ce que cette classe fait — ouvrir une image, la
 * passer au décodeur, refermer — n'est vérifiable que sur un appareil. L'anti-rebond,
 * lui, est dans [SteadyBarcode] et s'éprouve sur la JVM.
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
internal class BarcodeAnalyzer(private val steady: SteadyBarcode, private val onBarcode: (Barcode) -> Unit) :
    ImageAnalysis.Analyzer {
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(FOOD_FORMATS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val frame = image.image
        if (frame == null) {
            image.close()
            return
        }

        scanner
            .process(InputImage.fromMediaImage(frame, image.imageInfo.rotationDegrees))
            .addOnSuccessListener { codes -> codes.forEach { it.rawValue?.let(::confirm) } }
            // L'image **doit** etre refermee dans tous les cas, succes comme echec :
            // la retenir bloque le flux, et l'apercu se fige sans que rien ne le dise.
            .addOnCompleteListener { image.close() }
    }

    private fun confirm(raw: String) {
        steady.read(raw)?.let(onBarcode)
    }
}

private val FOOD_FORMATS: BarcodeScannerOptions = BarcodeScannerOptions
    .Builder()
    .setBarcodeFormats(MlBarcode.FORMAT_EAN_13, MlBarcode.FORMAT_EAN_8, MlBarcode.FORMAT_UPC_A)
    .build()
