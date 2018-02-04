import io.reactivex.Flowable
import retrofit2.CallAdapter
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import java.lang.reflect.Type

class RxJava2ReauthCallAdapterFactory private constructor(
        private val reauthFlowable: Flowable<*>,
        private val executeReauthentication: (Throwable) -> Boolean,
        private val retries: Int
) : CallAdapter.Factory() {

    private val rxJava2CallAdapterFactory: RxJava2CallAdapterFactory = RxJava2CallAdapterFactory.create()

    @Suppress("UNCHECKED_CAST")
    override fun get(returnType: Type, annotations: Array<out Annotation>, retrofit: Retrofit): CallAdapter<*, *>? {
        return RxJava2ReauthCallAdapter(
                rxJava2CallAdapterFactory.get(returnType, annotations, retrofit) as CallAdapter<Any, Any>?,
                reauthFlowable,
                executeReauthentication,
                retries
        )
    }

    companion object {
        fun create(
                reauthFlowable: Flowable<*>,
                executeReauthentication: (Throwable) -> Boolean = { true },
                retries: Int = 1
        ): CallAdapter.Factory {
            if (retries < 1) throw IllegalArgumentException("Amount of retries should be at least 1")
            return RxJava2ReauthCallAdapterFactory(reauthFlowable, executeReauthentication, retries)
        }
    }
}
