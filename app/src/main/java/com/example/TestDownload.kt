import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import android.content.Context
import android.util.Log

object TestDownload {
    fun test(context: Context) {
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.NIGHTLY)
            var request = YoutubeDLRequest("https://youtube.com/shorts/-eZtIQ8dLFw")
            var info = YoutubeDL.getInstance().getInfo(request)
            Log.d("TestDL", "NIGHTLY Success! " + info.title)
        } catch (e: Exception) {
            Log.e("TestDL", "NIGHTLY Error", e)
        }
    }
}
