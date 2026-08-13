package app.hexaphore.integration.scanner

import android.graphics.Bitmap
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
 * passer au décodeur, en garder la trame, refermer — n'est vérifiable que sur un
 * appareil. L'anti-rebond, lui, est dans [SteadyBarcode] et s'éprouve sur la JVM ;
 * la réduction de la trame aussi, dans [frameScale].
 *
 * [parcours]: docs/02-parcours-et-ecrans.md
 * [decisions]: docs/11-decisions.md
 */
internal class BarcodeAnalyzer(private val steady: SteadyBarcode, private val onBarcode: (Barcode, Bitmap?) -> Unit) :
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
            .addOnSuccessListener { codes -> confirm(codes, image) }
            // L'image **doit** etre refermee dans tous les cas, succes comme echec :
            // la retenir bloque le flux, et l'apercu se fige sans que rien ne le dise.
            .addOnCompleteListener { image.close() }
    }

    /** Le décodeur tient un client natif : le refermer est la contrepartie de sa création. */
    fun close() {
        scanner.close()
    }

    /**
     * La confirmation, et le seul endroit d'où sort une trame.
     *
     * **C'est ici et nulle part ailleurs** : [SteadyBarcode] ne rend un code qu'à la
     * seconde lecture d'accord, donc cette ligne s'exécute une fois par scan, sur
     * l'image même qui a porté l'accord. Capturer ailleurs — plus tôt, à chaque
     * image — coûterait une conversion trente fois par seconde ; plus tard, on
     * n'aurait plus l'`ImageProxy`, qui est refermé dès que la tâche s'achève.
     *
     * L'écouteur de ML Kit s'exécute sur le **fil principal** faute d'exécuteur donné,
     * et c'est ce qui rend légal ce que l'appelant en fait — délier la caméra, écrire
     * un état de composition. La conversion y coûte quelques millisecondes, une fois.
     */
    private fun confirm(codes: List<MlBarcode>, image: ImageProxy) {
        val settled = codes.firstNotNullOfOrNull { it.rawValue?.let(steady::read) } ?: return
        onBarcode(settled, image.capture())
    }
}

private val FOOD_FORMATS: BarcodeScannerOptions = BarcodeScannerOptions
    .Builder()
    .setBarcodeFormats(MlBarcode.FORMAT_EAN_13, MlBarcode.FORMAT_EAN_8, MlBarcode.FORMAT_UPC_A)
    .build()
