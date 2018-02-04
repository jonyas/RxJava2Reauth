import io.reactivex.*
import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

internal class RxJava2ReauthCallAdapter<R>(
        private val wrappedAdapter: CallAdapter<R, Any>?,
        private val reauthFlowable: Flowable<*>,
        private val executeReauthentication: (Throwable) -> Boolean,
        private val retries: Int
) : CallAdapter<R, Any> {

    override fun adapt(call: Call<R>): Any {
        val rxObject = wrappedAdapter?.adapt(call)
        return if (rxObject is Observable<*>) {
            rxObject.retryWhen({ observable: Observable<Throwable> ->
                var retried = 0
                observable.flatMap {
                    if (executeReauthentication(it) && retried++ < retries) {
                        reauthFlowable.toObservable()
                    } else {
                        Observable.error<Throwable>(it)
                    }
                }
            })
        } else {
            val reauth = { flowable: Flowable<Throwable> ->
                var retried = 0
                flowable.flatMap {
                    if (executeReauthentication(it) && retried++ < retries) {
                        reauthFlowable
                    } else {
                        Flowable.error<Throwable>(it)
                    }
                }
            }
            when (rxObject) {
                is Single<*> -> rxObject.retryWhen(reauth)
                is Flowable<*> -> rxObject.retryWhen(reauth)
                is Maybe<*> -> rxObject.retryWhen(reauth)
                is Completable -> rxObject.retryWhen(reauth)
                else -> throw IllegalArgumentException("$javaClass type is not supported")
            }
        }
    }

    override fun responseType(): Type? = wrappedAdapter?.responseType()

}