package com.tyganeutronics.myratecalculator.activities

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.analytics.FirebaseAnalytics
import com.maltaisn.calcdialog.CalcDialog
import com.maltaisn.calcdialog.CalcNumpadLayout
import com.tyganeutronics.myratecalculator.Calculator
import com.tyganeutronics.myratecalculator.MyApplication
import com.tyganeutronics.myratecalculator.R
import com.tyganeutronics.myratecalculator.contract.CurrencyContract
import com.tyganeutronics.myratecalculator.fragments.FragmentCalculator
import com.tyganeutronics.myratecalculator.models.*
import com.tyganeutronics.myratecalculator.utils.BaseUtils
import com.tyganeutronics.myratecalculator.widget.MultipleRateProvider
import com.tyganeutronics.myratecalculator.widget.SingleRateProvider
import kotlinx.android.synthetic.main.layout_amount.*
import kotlinx.android.synthetic.main.layout_main.*
import kotlinx.android.synthetic.main.layout_rates.*
import kotlinx.android.synthetic.main.layout_result.view.*
import org.json.JSONObject
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

class MainActivity : BaseActivity(), TextWatcher, AdapterView.OnItemSelectedListener,
    Response.Listener<JSONObject>, Response.ErrorListener, SwipeRefreshLayout.OnRefreshListener,
    View.OnClickListener, CalcDialog.CalcDialogCallback {

    private val fragmentCalculator: FragmentCalculator = FragmentCalculator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout_main)
        setupAd()

        bindViews()
        syncViews()
        textWatchers()

        if (shouldUpdate()) {
            fetchRates()
        }
    }

    private fun textWatchers() {
        et_bond.addTextChangedListener(this)
        et_omir.addTextChangedListener(this)
        et_rtgs.addTextChangedListener(this)
        et_rbz.addTextChangedListener(this)
        et_zar.addTextChangedListener(this)
        et_amount.addTextChangedListener(this)
    }

    /**
     * bind layout views
     */
    private fun bindViews() {
        sr_layout.setOnRefreshListener(this)

        s_currency.onItemSelectedListener = this

        btn_toggle.setOnClickListener(this)

        //calculator btns
        ib_bond_calculator.setOnClickListener(this)
        ib_omir_calculator.setOnClickListener(this)
        ib_rtgs_calculator.setOnClickListener(this)
        ib_rbz_calculator.setOnClickListener(this)
        ib_zar_calculator.setOnClickListener(this)
        ib_amount_calculator.setOnClickListener(this)

        triggerRateDialog()
    }

    /**
     * Sync layout views
     */
    private fun syncViews() {

        et_bond.setText(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_bond),
                "1"
            )
        )
        et_omir.setText(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_omir),
                "1"
            )
        )
        et_rtgs.setText(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_rtgs),
                "1"
            )
        )
        et_rbz.setText(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_rbz),
                "1"
            )
        )
        et_zar.setText(
            BaseUtils.getPrefs(this).getString(
                getString(R.string.currency_zar),
                "1"
            )
        )

        s_currency.setSelection(BaseUtils.getPrefs(this).getInt(CurrencyContract.CURRENCY, 0))

        et_amount.text?.append(
            BaseUtils.getPrefs(this).getString(
                "amount",
                "1"
            )
        )

        fragmentCalculator.settings.apply {
            minValue = BigDecimal("-1e10")
            maxValue = BigDecimal("1e10")
            numpadLayout = CalcNumpadLayout.CALCULATOR
            isExpressionShown = true
            isAnswerBtnShown = true
        }

        sr_layout.setColorSchemeResources(R.color.colorAccent)

    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.btn_toggle -> {

                FirebaseAnalytics.getInstance(baseContext).logEvent("toggle_rates_parent", Bundle())

                btn_toggle.setCompoundDrawablesWithIntrinsicBounds(
                    null,
                    null,
                    ContextCompat.getDrawable(
                        baseContext,
                        run {
                            layout_rates.isVisible = layout_rates.isVisible.not()
                            val drawable: Int =
                                if (layout_rates.isVisible) R.drawable.ic_expand_less else R.drawable.ic_expand_more
                            drawable
                        }
                    ),
                    null
                )

            }
            R.id.ib_bond_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_bond.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
            R.id.ib_omir_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_omir.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
            R.id.ib_rtgs_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_rtgs.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
            R.id.ib_rbz_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_rbz.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
            R.id.ib_zar_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_zar.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
            R.id.ib_amount_calculator -> {
                fragmentCalculator.settings.apply {
                    initialValue = et_amount.text.let {
                        val text = if (it.isNullOrBlank()) "0" else it
                        text
                    }.toString().toBigDecimal()
                    requestCode = view.id
                }

                fragmentCalculator.show(supportFragmentManager, "CalcDialog")
            }
        }
    }

    override fun onRefresh() {
        sr_layout.isRefreshing = true

        getFirebaseAnalytics()!!.logEvent("refresh_rates", Bundle())

        fetchRates()
    }

    private fun shouldUpdate(): Boolean {
        var check = BaseUtils.getPrefs(baseContext).getBoolean("check_update", true)

        if (check) {

            val last = BaseUtils.getPrefs(baseContext).getLong(CurrencyContract.LAST_CHECK, 0L)
            val offset = BaseUtils.getPrefs(baseContext).getString("update_interval", "1")

            val hours = TimeUnit.HOURS.toMillis(offset!!.toLong())

            check = check.and(System.currentTimeMillis() > last.plus(hours))
        }

        return check
    }

    /**
     * Do fetch server rates
     */
    private fun fetchRates() {

        val prefer = BaseUtils.getPrefs(applicationContext)
            .getString("preferred_currency", getString(R.string.prefer_max))

        val uri = Uri.parse(getString(R.string.rates_url)).buildUpon()
            .appendQueryParameter(CurrencyContract.PREFER, prefer).build()

        val jsonObjectRequest = JsonObjectRequest(uri.toString(), null, this, this)

        jsonObjectRequest.setShouldCache(false)
        jsonObjectRequest.retryPolicy = DefaultRetryPolicy(10000, 2, 1.0f)

        MyApplication.requestQueue.add(jsonObjectRequest)
    }

    override fun onResponse(response: JSONObject?) {

        sr_layout.isRefreshing = false

        if (BaseUtils.getPrefs(baseContext).getBoolean("auto_update", true)) {
            updateCurrencies(response)
        } else {

            Snackbar.make(
                calc_result_layout,
                R.string.update_available,
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(
                    R.string.update_apply
                ) {
                    updateCurrencies(response)
                }.show()
        }

    }

    private fun updateCurrencies(response: JSONObject?) {
        val currencies = JSONObject(response.toString()).optJSONArray(CurrencyContract.USD)

        if (currencies != null) {

            for (i in 0 until currencies.length()) {
                val currency = currencies.getJSONObject(i)

                val instant = Instant.ofEpochSecond(
                    currency.getString(CurrencyContract.LAST_UPDATED).toLong()
                )
                val format =
                    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
                val date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(format)

                val rate = currency.getString(CurrencyContract.RATE)

                when (currency.getString(CurrencyContract.CURRENCY)) {
                    getString(R.string.currency_bond) -> {
                        et_bond.setText(rate)
                        et_bond_parent.helperText = date
                    }
                    getString(R.string.currency_omir) -> {
                        et_omir.setText(rate)
                        et_omir_parent.helperText = date
                    }
                    getString(R.string.currency_rbz) -> {
                        et_rbz.setText(rate)
                        et_rbz_parent.helperText = date
                    }
                    getString(R.string.currency_rtgs) -> {
                        et_rtgs.setText(rate)
                        et_rtgs_parent.helperText = date
                    }
                    getString(R.string.currency_zar) -> {
                        et_zar.setText(rate)
                        et_zar_parent.helperText = date
                    }
                }
            }

            saveRates()

            BaseUtils.getPrefs(baseContext).edit()
                .putLong(CurrencyContract.LAST_CHECK, System.currentTimeMillis())
                .apply()
        }
    }

    override fun onErrorResponse(error: VolleyError?) {
        error?.printStackTrace()

        sr_layout.isRefreshing = false
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {

    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        FirebaseAnalytics.getInstance(baseContext).logEvent("change_currency", Bundle())

        calculate()
    }

    override fun afterTextChanged(s: Editable?) {
        calculate()
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.calculator, menu)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_settings -> {

                val intent = Intent(baseContext, SettingsActivity().javaClass)
                startActivity(intent)

                return true
            }
            R.id.menu_info -> {

                FirebaseAnalytics.getInstance(baseContext).logEvent("view_info_dialog", Bundle())

                val dialog = AlertDialog.Builder(this)
                dialog.setTitle(R.string.menu_info)
                dialog.setIcon(ContextCompat.getDrawable(baseContext, R.drawable.ic_info))
                dialog.setMessage(R.string.info_message)
                dialog.setPositiveButton(R.string.info_dismiss, null)
                dialog.show()

                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun saveRates() {
        BaseUtils.getPrefs(this).edit()
            .putString(getString(R.string.currency_bond), et_bond.text?.toString())
            .apply()
        BaseUtils.getPrefs(this).edit()
            .putString(getString(R.string.currency_omir), et_omir.text?.toString())
            .apply()
        BaseUtils.getPrefs(this).edit()
            .putString(getString(R.string.currency_rtgs), et_rtgs.text?.toString())
            .apply()
        BaseUtils.getPrefs(this).edit()
            .putString(getString(R.string.currency_rbz), et_rbz.text?.toString())
            .apply()
        BaseUtils.getPrefs(this).edit()
            .putString(getString(R.string.currency_zar), et_zar.text?.toString())
            .apply()
    }

    private fun getCalculator(): Calculator {

        var bondText = normaliseInput(et_bond.text?.toString())
        var omirText = normaliseInput(et_omir.text?.toString())
        var rtgsText = normaliseInput(et_rtgs.text?.toString())
        var rbzText = normaliseInput(et_rbz.text?.toString())
        var zarText = normaliseInput(et_zar.text?.toString())

        if (TextUtils.isEmpty(bondText)) {
            bondText = "1"
        }
        if (TextUtils.isEmpty(omirText)) {
            omirText = "1"
        }
        if (TextUtils.isEmpty(rtgsText)) {
            rtgsText = "1"
        }
        if (TextUtils.isEmpty(rbzText)) {
            rbzText = "1"
        }
        if (TextUtils.isEmpty(zarText)) {
            zarText = "1"
        }

        saveRates()

        val usd = USD(1.0)
        val bond = BOND(bondText.toDouble())
        val omir = OMIR(omirText.toDouble())
        val rtgs = RTGS(rtgsText.toDouble())
        val rbz = RBZ(rbzText.toDouble())
        val zar = ZAR(zarText.toDouble())

        var currency: Currency = usd
        when (s_currency.selectedItem) {
            getString(R.string.currency_usd) -> {
                currency = usd
            }
            getString(R.string.currency_omir) -> {
                currency = omir
            }
            getString(R.string.currency_bond) -> {
                currency = bond
            }
            getString(R.string.currency_rtgs) -> {
                currency = rtgs
            }
            getString(R.string.currency_rbz) -> {
                currency = rbz
            }
            getString(R.string.currency_zar) -> {
                currency = zar
            }
        }

        return Calculator(
            usd,
            bond,
            omir,
            rtgs,
            rbz,
            zar,
            currency
        )
    }

    private fun isNumeric(input: String): Boolean {
        return try {
            input.toDouble()
            true
        } catch (e: NumberFormatException) {
            false
        }
    }

    private fun normaliseInput(input: String?): String {
        if (input == null || input.isEmpty() || !isNumeric(input)) {
            return "1"
        }
        return input
    }

    private fun calculate() {

        val calculator = getCalculator()

        val amountText = normaliseInput(et_amount.text?.toString())

        if (amountText.isNotEmpty()) {

            //save amount entered
            BaseUtils.getPrefs(this).edit().putString("amount", amountText)
                .apply()
            BaseUtils.getPrefs(this).edit().putInt("currency", s_currency.selectedItemPosition)
                .apply()

            calc_result_layout.removeAllViews()

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toUSD(amountText.toDouble()),
                calculator.usd
            )

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toBOND(amountText.toDouble()),
                calculator.bond
            )

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toOMIR(amountText.toDouble()),
                calculator.omir
            )

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toRBZ(amountText.toDouble()),
                calculator.rbz
            )

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toRTGS(amountText.toDouble()),
                calculator.rtgs
            )

            calculateCurrencyResult(
                amountText.toDouble(),
                calculator.toZAR(amountText.toDouble()),
                calculator.zar
            )
        }

    }

    private fun calculateCurrencyResult(amountText: Double, result: Double, currency: Currency) {

        addResultLayout(
            getResultText(
                amountText,
                currency.getSign(),
                result,
                getString(currency.getName())
            ), result, getString(currency.getName())
        )
    }

    /**
     * get calculated result text
     */
    private fun getResultText(
        amountText: Double,
        sign: String,
        result: Double,
        currency: String
    ): String {
        val calculator = getCalculator()

        return getString(
            R.string.result,
            calculator.currency.getSign(),
            amountText,
            s_currency.selectedItem,
            sign,
            result,
            currency
        )
    }

    /**
     * create result layout
     */
    private fun addResultLayout(result: String, amount: Double, currency: String) {

        val resultLayout: CardView =
            layoutInflater.inflate(
                R.layout.layout_result,
                calc_result_layout,
                false
            ) as CardView

        resultLayout.result_text.text = result
        resultLayout.setOnClickListener {

            et_amount.setText(String.format("%10.2f", amount).trim())

            val selection = resources.getStringArray(R.array.currencies).indexOf(currency)

            FirebaseAnalytics.getInstance(baseContext)
                .logEvent("copy_result_for_calculation", Bundle())

            s_currency.setSelection(selection)
        }

        calc_result_layout.addView(resultLayout)
    }

    override fun onStop() {
        super.onStop()

        updateMultipleWidget()
        updateSingleWidget()
    }

    private fun updateMultipleWidget() {

        val componentName = ComponentName(this, MultipleRateProvider::class.java)

        val appWidgetManager = AppWidgetManager.getInstance(this)

        val intent = Intent(this, MultipleRateProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val multipleIds = appWidgetManager.getAppWidgetIds(componentName)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, multipleIds)
        sendBroadcast(intent)
    }

    private fun updateSingleWidget() {

        val componentName = ComponentName(this, SingleRateProvider::class.java)

        val appWidgetManager = AppWidgetManager.getInstance(this)

        val intent = Intent(this, SingleRateProvider::class.java)
        intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE

        val multipleIds = appWidgetManager.getAppWidgetIds(componentName)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, multipleIds)
        sendBroadcast(intent)
    }

    override fun onValueEntered(requestCode: Int, value: BigDecimal?) {
        when (requestCode) {
            R.id.ib_bond_calculator -> {
                et_bond.setText(value?.toPlainString() ?: "")
            }
            R.id.ib_omir_calculator -> {
                et_omir.setText(value?.toPlainString() ?: "")
            }
            R.id.ib_rtgs_calculator -> {
                et_rtgs.setText(value?.toPlainString() ?: "")
            }
            R.id.ib_rbz_calculator -> {
                et_rbz.setText(value?.toPlainString() ?: "")
            }
            R.id.ib_zar_calculator -> {
                et_zar.setText(value?.toPlainString() ?: "")
            }
            R.id.ib_amount_calculator -> {
                et_amount.setText(value?.toPlainString() ?: "")
            }
        }
    }

}
