package foundation.omni.test.rpc.basic

import foundation.omni.BaseRegTestSpec
import foundation.omni.CurrencyID
import foundation.omni.Ecosystem
import foundation.omni.PropertyType
import org.junit.internal.AssumptionViolatedException

class GetTransactionSpec extends BaseRegTestSpec {

    final static BigDecimal startBTC = 1.001
    final static BigDecimal startMSC = 0.001
    final static BigDecimal zeroAmount = 0.0

    def "Tx 0: Simple Send"() {
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def otherAddress = newAddress

        when: "unconfirmed"
        def txid = omniSend(actorAddress, otherAddress, CurrencyID.MSC, startMSC)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        tx.referenceaddress == otherAddress.toString()
        tx.confirmations == 0
        tx.type == "Simple Send"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == startMSC

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        tx.referenceaddress == otherAddress.toString()
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 0
        tx.type == "Simple Send"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == startMSC
    }

    def "Tx 3: Send To Owners"() {
        def sendAmount = new BigDecimal('0.00000001')
        def actorAddress = createFundedAddress(startBTC, startMSC)
        createFundedAddress(startBTC, startMSC) // to have at least one owner

        when: "unconfirmed"
        def txid = omniSendSTO(actorAddress, CurrencyID.MSC, sendAmount)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "Send To Owners"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == sendAmount

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 3
        tx.type == "Send To Owners"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == sendAmount
    }

    def "Tx 20: DEx Sell Offer - New"() {
        BigDecimal tradeAmountMSC = new BigDecimal('0.00000002')
        BigDecimal tradeAmountBTC = new BigDecimal('0.00000001')
        Byte paymentWindow = 10
        BigDecimal minTxFees = new BigDecimal('0.00000003')
        Byte actionNew = 1
        def actorAddress = createFundedAddress(startBTC, startMSC)

        when: "unconfirmed"
        def txid = omniSendDExSell(actorAddress, CurrencyID.MSC, tradeAmountMSC, tradeAmountBTC, paymentWindow, minTxFees,
                                   actionNew)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.action == "new"
        (tx.bitcoindesired as BigDecimal) == tradeAmountBTC

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 1
        tx.type_int == 20
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == tradeAmountMSC
        (tx.bitcoindesired as BigDecimal) == tradeAmountBTC
        tx.timelimit == paymentWindow
        (tx.feerequired as BigDecimal) == minTxFees
        tx.action == "new"

    }

    def "Tx 20: DEx Sell Offer - Update"() {
        // Initial offer
        BigDecimal tradeAmountMSC = new BigDecimal('0.00000002')
        BigDecimal tradeAmountBTC = new BigDecimal('0.00000001')
        Byte paymentWindow = 10
        BigDecimal minTxFees = new BigDecimal('0.00000003')
        Byte actionNew = 1
        // Updated offer
        BigDecimal tradeAmountUpdatedMSC = new BigDecimal('0.00000004')
        BigDecimal tradeAmountUpdatedBTC = new BigDecimal('0.00000005')
        Byte paymentWindowUpdated = 11
        BigDecimal minTxFeesUpdated = new BigDecimal('0.00000006')
        Byte actionUpdate = 2
        def actorAddress = createFundedAddress(startBTC, startMSC)

        when: "unconfirmed"
        omniSendDExSell(actorAddress, CurrencyID.MSC, tradeAmountMSC, tradeAmountBTC, paymentWindow, minTxFees, actionNew)
        generateBlock()
        def txid = omniSendDExSell(actorAddress, CurrencyID.MSC, tradeAmountUpdatedMSC, tradeAmountUpdatedBTC,
                                   paymentWindowUpdated, minTxFeesUpdated, actionUpdate)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        (tx.amount as BigDecimal) == tradeAmountUpdatedMSC
        tx.action == "update"
        (tx.bitcoindesired as BigDecimal) == tradeAmountUpdatedBTC

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 1
        tx.type_int == 20
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == tradeAmountUpdatedMSC
        (tx.bitcoindesired as BigDecimal) == tradeAmountUpdatedBTC
        tx.timelimit == paymentWindowUpdated
        (tx.feerequired as BigDecimal) == minTxFeesUpdated
        tx.action == "update"
    }

    def "Tx 20: DEx Sell Offer - Cancel"() {
        // Initial offer
        BigDecimal tradeAmountMSC = new BigDecimal('0.00000002')
        BigDecimal tradeAmountBTC = new BigDecimal('0.00000001')
        Byte paymentWindow = 10
        BigDecimal minTxFees = new BigDecimal('0.00000003')
        Byte actionNew = 1
        // Updated offer
        BigDecimal tradeAmountIgnoredMSC = new BigDecimal('0.00000004')
        BigDecimal tradeAmountIgnoredBTC = new BigDecimal('0.00000005')
        Byte paymentWindowIgnored = 11
        BigDecimal minTxFeesIgnored = new BigDecimal('0.00000006')
        Byte actionCancel = 3
        def actorAddress = createFundedAddress(startBTC, startMSC)

        when: "unconfirmed"
        omniSendDExSell(actorAddress, CurrencyID.MSC, tradeAmountMSC, tradeAmountBTC, paymentWindow, minTxFees, actionNew)
        generateBlock()
        def txid = omniSendDExSell(actorAddress, CurrencyID.MSC, tradeAmountIgnoredMSC, tradeAmountIgnoredBTC,
                                   paymentWindowIgnored, minTxFeesIgnored, actionCancel)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        (tx.amount as BigDecimal) == zeroAmount
        tx.action == "cancel"
        (tx.bitcoindesired as BigDecimal) == zeroAmount

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 1
        tx.type_int == 20
        tx.type == "DEx Sell Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == zeroAmount
        (tx.bitcoindesired as BigDecimal) == zeroAmount
        tx.timelimit == 0
        (tx.feerequired as BigDecimal) == zeroAmount
        tx.action == "cancel"
    }

    def "Tx 22: DEx Accept Order"() {
        // Initial offer
        BigDecimal tradeAmountMSC = new BigDecimal('0.00000010')
        BigDecimal tradeAmountBTC = new BigDecimal('0.00000005')
        Byte paymentWindow = 10
        BigDecimal minTxFees = stdTxFee * 5
        Byte actionNew = 1
        // Accept order
        BigDecimal acceptAmountMSC = new BigDecimal('0.00000005')

        def otherAddress = createFundedAddress(startBTC, startMSC)
        def actorAddress = createFundedAddress(startBTC + minTxFees, startMSC)

        // TODO: test with low fee settings

        when: "confirmed"
        omniSendDExSell(otherAddress, CurrencyID.MSC, tradeAmountMSC, tradeAmountBTC, paymentWindow, minTxFees, actionNew)
        generateBlock()
        def txid = omniSendDExAccept(actorAddress, otherAddress, CurrencyID.MSC, acceptAmountMSC, true)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        tx.referenceaddress == otherAddress.toString()
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) >= minTxFees
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 22
        tx.type == "DEx Accept Offer"
        tx.propertyid == CurrencyID.MSC_VALUE
        tx.divisible
        (tx.amount as BigDecimal) == acceptAmountMSC
    }

    def "Tx 25: MetaDEx - Trade"() {
        def amountForSale = startMSC
        def amountDesired = startMSC * 2
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def nonManagedID = fundNewProperty(actorAddress, amountDesired, PropertyType.DIVISIBLE, Ecosystem.TMSC)

        when: "unconfirmed"
        def txid = omniSendTrade(actorAddress, CurrencyID.TMSC, amountForSale, nonManagedID, amountDesired)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "MetaDEx trade"
        tx.propertyidforsale == CurrencyID.TMSC_VALUE
        tx.propertyidforsaleisdivisible
        (tx.amountforsale as BigDecimal) == amountForSale
        tx.propertyiddesired == nonManagedID.longValue()
        tx.propertyiddesiredisdivisible
        (tx.amountdesired as BigDecimal) == amountDesired
        (tx.unitprice as BigDecimal) == (amountForSale / amountDesired) // always nominated in MSC

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 25
        tx.type == "MetaDEx trade"
        tx.propertyidforsale == CurrencyID.TMSC_VALUE
        tx.propertyidforsaleisdivisible
        (tx.amountforsale as BigDecimal) == amountForSale
        (tx.amountremaining as BigDecimal) == amountForSale
        tx.propertyiddesired == nonManagedID.longValue()
        tx.propertyiddesiredisdivisible
        (tx.amountdesired as BigDecimal) == amountDesired
        (tx.amounttofill as BigDecimal) == amountDesired
        (tx.unitprice as BigDecimal) == (amountForSale / amountDesired) // always nominated in MSC
    }

    def "Tx 26: MetaDEx - Cancel By Price"() {
        def amountForSale = startMSC
        def amountDesired = startMSC * 2
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def nonManagedID = fundNewProperty(actorAddress, amountDesired, PropertyType.DIVISIBLE, Ecosystem.TMSC)

        when: "unconfirmed"
        omniSendTrade(actorAddress, CurrencyID.TMSC, amountForSale, nonManagedID, amountDesired)
        generateBlock()
        def txid = omniSendCancelTradesByPrice(actorAddress, CurrencyID.TMSC, amountForSale, nonManagedID,
                                               amountDesired)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "MetaDEx cancel-price"
        tx.propertyidforsale == CurrencyID.TMSC_VALUE
        tx.propertyidforsaleisdivisible
        (tx.amountforsale as BigDecimal) == amountForSale
        tx.propertyiddesired == nonManagedID.longValue()
        tx.propertyiddesiredisdivisible
        (tx.amountdesired as BigDecimal) == amountDesired
        (tx.unitprice as BigDecimal) == (amountForSale / amountDesired) // always nominated in MSC

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 26
        tx.type == "MetaDEx cancel-price"
        tx.propertyidforsale == CurrencyID.TMSC_VALUE
        tx.propertyidforsaleisdivisible
        (tx.amountforsale as BigDecimal) == amountForSale
        tx.propertyiddesired == nonManagedID.longValue()
        tx.propertyiddesiredisdivisible
        (tx.amountdesired as BigDecimal) == amountDesired
        (tx.unitprice as BigDecimal) == (amountForSale / amountDesired) // always nominated in MSC
    }

    def "Tx 27: MetaDEx - Cancel By Pair"() {
        def amountForSale = startMSC * 5
        def amountDesired = startMSC
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def nonManagedID = fundNewProperty(actorAddress, amountForSale, PropertyType.DIVISIBLE, Ecosystem.TMSC)

        when: "unconfirmed"
        omniSendTrade(actorAddress, nonManagedID, amountForSale, CurrencyID.TMSC, amountDesired)
        generateBlock()
        def txid = omniSendCancelTradesByPair(actorAddress, nonManagedID, CurrencyID.TMSC)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "MetaDEx cancel-pair"
        tx.propertyidforsale == nonManagedID.longValue()
        tx.propertyiddesired == CurrencyID.TMSC_VALUE

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 27
        tx.type == "MetaDEx cancel-pair"
        tx.propertyidforsale == nonManagedID.longValue()
        tx.propertyiddesired == CurrencyID.TMSC_VALUE
    }

    def "Tx 28: MetaDEx - Cancel All"() {
        def amountForSale = startMSC
        def amountDesired = startMSC
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def nonManagedID = fundNewProperty(actorAddress, amountForSale, PropertyType.DIVISIBLE, Ecosystem.TMSC)

        when: "unconfirmed"
        omniSendTrade(actorAddress, nonManagedID, amountForSale, CurrencyID.TMSC, amountDesired)
        generateBlock()
        def txid = omniSendCancelAllTrades(actorAddress, Ecosystem.TMSC)
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.confirmations == 0
        tx.type == "MetaDEx cancel-ecosystem"
        tx.ecosystem == "test"

        when: "confirmed"
        generateBlock()
        tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 28
        tx.type == "MetaDEx cancel-ecosystem"
        tx.ecosystem == "test"
    }

    def "Tx 50: Create Property - Fixed"() {
        Ecosystem ecosystem = Ecosystem.MSC
        PropertyType propertyType = PropertyType.INDIVISIBLE
        CurrencyID previousId = new CurrencyID(0) // new
        String category = "Test category"
        String subCategory = "Test subcategory"
        String name = "Test tokens"
        String url = "http://www.omnilayer.org/"
        String data = "This is a test for tx 50."
        BigDecimal amount = 500

        def actorAddress = createFundedAddress(startBTC, startMSC)

        when: "confirmed"
        def txid = omniSendIssuanceFixed(actorAddress, ecosystem, propertyType, previousId, category, subCategory, name,
                                         url, data, amount)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 50
        tx.type == "Create Property - Fixed"
        tx.propertyid > CurrencyID.TMSC_VALUE
        !tx.divisible
        tx.ecosystem == "main"
        tx.propertytype == "indivisible"
        tx.category == category
        tx.subcategory == subCategory
        tx.propertyname == name
        tx.url == url
        tx.data == data
        (tx.amount as BigDecimal) == amount
    }

    def "Tx 51: Create Property - Variable"() {
        Ecosystem ecosystem = Ecosystem.TMSC
        PropertyType propertyType = PropertyType.DIVISIBLE
        CurrencyID previousId = new CurrencyID(0) // new
        String category = "Test category"
        String subCategory = "Test subcategory"
        String name = "Test crowdsale"
        String url = "http://www.omnilayer.org/"
        String data = "This is a test for tx 51."
        CurrencyID propertyDesired = CurrencyID.TMSC
        BigDecimal tokensPerUnit = 0.1
        Long deadline = 1597177574
        Byte earlyBirdBonus = 10
        Byte issuerBonus = 5

        def actorAddress = createFundedAddress(startBTC, startMSC)

        when: "confirmed"
        def txid = omniSendIssuanceCrowdsale(actorAddress, ecosystem, propertyType, previousId, category, subCategory, name,
                                             url, data, propertyDesired, tokensPerUnit, deadline, earlyBirdBonus,
                                             issuerBonus)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 51
        tx.type == "Create Property - Variable"
        tx.propertyid > CurrencyID.TMSC_VALUE
        tx.ecosystem == "test"
        tx.propertytype == "divisible"
        tx.category == category
        tx.subcategory == subCategory
        tx.propertyname == name
        tx.url == url
        tx.data == data
        (tx.tokensperunit as BigDecimal) == tokensPerUnit
        tx.deadline == deadline
        tx.earlybonus == earlyBirdBonus
        tx.percenttoissuer == issuerBonus
    }

    def "Tx 53: Close Crowdsale"() {
        throw new AssumptionViolatedException('not implemented')
    }

    def "Tx 54: Create Property - Manual"() {
        throw new AssumptionViolatedException('not implemented')
    }

    def "Tx 55: Grant Property Tokens"() {
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def otherAddress = newAddress
        def managedID = fundManagedProperty(actorAddress, PropertyType.DIVISIBLE, Ecosystem.MSC)
        def amount = 50.0

        when: "confirmed"
        def txid = omniSendGrant(actorAddress, otherAddress, managedID, amount)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        tx.referenceaddress == otherAddress.toString()
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 55
        tx.type == "Grant Property Tokens"
        tx.propertyid == managedID.longValue()
        tx.divisible
        (tx.amount as BigDecimal) == amount
    }

    def "Tx 56: Revoke Property Tokens"() {
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def managedID = fundManagedProperty(actorAddress, PropertyType.INDIVISIBLE, Ecosystem.TMSC)
        def amountGrant = 500L
        def amountRevoke = 21L

        when: "confirmed"
        omniSendGrant(actorAddress, actorAddress, managedID, amountGrant)
        generateBlock()
        def txid = omniSendRevoke(actorAddress, managedID, amountRevoke)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        !tx.containsKey('referenceaddress')
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 56
        tx.type == "Revoke Property Tokens"
        tx.propertyid == managedID.longValue()
        !tx.divisible
        (tx.amount as Long) == amountRevoke
    }

    def "Tx 70: Change Issuer Address"() {
        def actorAddress = createFundedAddress(startBTC, startMSC)
        def otherAddress = newAddress
        def managedID = fundManagedProperty(actorAddress, PropertyType.INDIVISIBLE, Ecosystem.MSC)

        when: "confirmed"
        def txid = omniSendChangeIssuer(actorAddress, otherAddress, managedID)
        generateBlock()
        def tx = omniGetTransaction(txid)

        then:
        tx.txid == txid.toString()
        tx.sendingaddress == actorAddress.toString()
        tx.referenceaddress == otherAddress.toString()
        tx.ismine
        tx.confirmations == 1
        (tx.fee as BigDecimal) > 0.0
        (tx.blocktime as Long) > 0
        tx.valid
        tx.version == 0
        tx.type_int == 70
        tx.type == "Change Issuer Address"
        tx.propertyid == managedID.longValue()
        !tx.divisible
    }

}
