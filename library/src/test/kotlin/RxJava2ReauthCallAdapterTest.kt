import io.reactivex.Flowable
import io.reactivex.Single
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.http.GET
import retrofit2.mock.BehaviorDelegate
import retrofit2.mock.Calls
import retrofit2.mock.MockRetrofit
import retrofit2.mock.NetworkBehavior
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit


class RxJava2ReauthCallAdapterTest {

    private lateinit var delegate: BehaviorDelegate<TestAuthenticationRestApi>

    @Before
    fun setup() {
        // Create a very simple Retrofit mock object to handle authentications
        val reauthRetrofit = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl("http://www.example.com/api/")
                .build()
        // Create a MockRetrofit object with a NetworkBehavior which manages the fake behavior of calls.
        val mockRetrofit = MockRetrofit.Builder(reauthRetrofit)
                .networkBehavior(NetworkBehavior.create().apply { setDelay(0, TimeUnit.MILLISECONDS) })
                .build()
        // Create the delegate that will be called when we request data from this mocked api
        delegate = mockRetrofit.create(TestAuthenticationRestApi::class.java)
    }

    @Test
    fun `Should properly reAuthenticate`() {

        var shouldBeOneAtTheEnd = 0
        // Create the reauthApi with the assertion logic for this testCase
        val testReauthRestApi = MockTestAuthenticationRestApi(delegate, { shouldBeOneAtTheEnd++ })
        // Create the mockTestRestApi with the test specific values
        val mockTestRestApi = buildMockTestRestApi(
                testReauthRestApi.authenticate("username", "password").toFlowable(),
                { true },
                1,
                IOException()
        )
        // Verify
        mockTestRestApi.loadSomeData()
                .test()
                .assertValue("Awesome Data")
        assert(shouldBeOneAtTheEnd == 1)
    }

    @Test
    fun `Should try reauthentication the amount of required times`() {

        val amountOfMaxRetries = 5
        var shouldBeFiveAtTheEnd = 0
        // Create the reauthApi with the assertion logic for this testCase
        val testReauthRestApi = MockTestAuthenticationRestApi(delegate, { shouldBeFiveAtTheEnd++ })
        // Create the mockTestRestApi with the test specific values
        val mockTestRestApi = buildMockTestRestApi(
                testReauthRestApi.authenticate("username", "password")
                        .toFlowable(),
                { true },
                amountOfMaxRetries,
                IOException()
        )
        // Verify
        mockTestRestApi.loadSomeData()
                .test()
                .assertValue("Awesome Data")
        assert(shouldBeFiveAtTheEnd == amountOfMaxRetries)
    }

    @Test
    fun `Should fail if reauthentication fails`() {

        var shouldBeOneAtTheEnd = 0
        // Create the reauthApi with the assertion logic for this testCase
        val testReauthRestApi = MockTestAuthenticationRestApi(delegate, { shouldBeOneAtTheEnd++ })
        // Create the mockTestRestApi with the test specific values
        val mockTestRestApi = buildMockTestRestApi(
                testReauthRestApi.authenticate("username", "password")
                        .toFlowable()
                        .flatMap { Flowable.error<Any>(Exception()) },
                { true },
                1,
                IOException()
        )
        // Verify
        mockTestRestApi.loadSomeData()
                .test()
                .assertError(Exception::class.java)
        assert(shouldBeOneAtTheEnd == 1)
    }

    @Test
    fun `Should not Reauthenticate if condition is not met`() {

        val exceptionToThrow = IOException()
        var shouldBeZeroAtTheEnd = 0
        // Create the reauthApi with the assertion logic for this testCase
        val testReauthRestApi = MockTestAuthenticationRestApi(delegate, { shouldBeZeroAtTheEnd++ })
        // Create the mockTestRestApi with the test specific values
        val mockTestRestApi = buildMockTestRestApi(
                testReauthRestApi.authenticate("username", "password")
                        .toFlowable()
                        .flatMap { Flowable.error<Any>(Exception()) },
                { it != exceptionToThrow },
                1,
                exceptionToThrow
        )
        // Verify
        mockTestRestApi.loadSomeData()
                .test()
                .assertError(Exception::class.java)
        // We never tried reauthentication
        assert(shouldBeZeroAtTheEnd == 0)
    }

    private fun buildMockTestRestApi(
            reauthFlowable: Flowable<*>,
            executeReauthentication: (Throwable) -> Boolean = { true },
            amountOfFailures: Int,
            exception: IOException
    ): MockTestRestApi {
        // Create a very simple Retrofit adapter which points to a random api
        // and it will use our auth api to reauthenticate
        val testRetrofitApi = Retrofit.Builder()
                .addCallAdapterFactory(RxJava2ReauthCallAdapterFactory.create(reauthFlowable, executeReauthentication, amountOfFailures))
                .baseUrl("http://www.example.com/api/")
                .build()
        val mockExampleRetrofit = MockRetrofit.Builder(testRetrofitApi)
                .networkBehavior(NetworkBehavior.create().apply { setDelay(0, TimeUnit.MILLISECONDS) })
                .build()
        val delegate = mockExampleRetrofit.create(TestRestApi::class.java)
        // Finally create the mock TestRestApi
        return MockTestRestApi(
                delegate,
                amountOfFailures,
                exception
        )
    }

}

interface TestAuthenticationRestApi {

    @GET
    fun authenticate(username: String, password: String): Single<String>

}

class MockTestAuthenticationRestApi(
        private val delegate: BehaviorDelegate<TestAuthenticationRestApi>,
        private val assertion: () -> Unit
) : TestAuthenticationRestApi {

    override fun authenticate(username: String, password: String): Single<String> {

        val call = Calls.defer {
            assertion()
            Calls.response("Awesome Data")
        }
        return delegate.returning(call).authenticate(username, password)
    }

}

interface TestRestApi {

    fun loadSomeData(): Single<String>

}

class MockTestRestApi(private val delegate: BehaviorDelegate<TestRestApi>,
                      private val amountOfFailures: Int,
                      private val errorToThrow: IOException
) : TestRestApi {


    override fun loadSomeData(): Single<String> {
        val call = Calls.defer(object : Callable<Call<String>> {
            private var currentAmountOfFailures = 0

            @Throws(Exception::class)
            override fun call(): Call<String> {
                if (currentAmountOfFailures++ < amountOfFailures) {
                    return Calls.failure<String>(errorToThrow)
                }
                return Calls.response("Awesome Data")
            }
        })
        return delegate.returning(call).loadSomeData()
    }

}
