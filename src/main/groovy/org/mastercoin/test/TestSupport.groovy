package org.mastercoin.test
import com.google.bitcoin.core.Address
import com.google.bitcoin.core.Sha256Hash
import com.google.bitcoin.core.Transaction
import com.msgilligan.bitcoin.BTC
import org.mastercoin.Ecosystem
import org.mastercoin.MPNetworkParameters
import org.mastercoin.MPRegTestParams
import org.mastercoin.PropertyType
import org.mastercoin.rpc.MastercoinClientDelegate

import java.security.SecureRandom

import static org.mastercoin.CurrencyID.MSC
import static org.mastercoin.CurrencyID.TMSC

/**
 * Test support functions intended to be mixed-in to Spock test specs
 */
trait TestSupport implements MastercoinClientDelegate {
    final BigDecimal stdTxFee = BTC.satoshisToBTC(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE)

    String createNewAccount() {
        def random = new SecureRandom();
        def accountName = "msc-" + new BigInteger(130, random).toString(32)
        return accountName
    }

    Address createFundedAddress(BigDecimal requestedBTC, BigDecimal requestedMSC) {
        final MPNetworkParameters params = MPRegTestParams.get()  // Hardcoded for RegTest for now
        Address stepAddress = getNewAddress()
        def btcForMSC = (requestedMSC / 100).setScale(8, BigDecimal.ROUND_UP)
        def startBTC = requestedBTC + btcForMSC + stdTxFee
        startBTC += 5 * stdTxFee  // To send BTC, MSC and TMSC to the real receiver

        // Generate blocks until we have the requested amount of BTC
        while (getBalance() < startBTC) {
            generateBlock()
        }
        Sha256Hash txid = sendToAddress(stepAddress, startBTC)
        generateBlock()
        def tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Make sure we got the correct amount of BTC
        BigDecimal btcBalance = getBitcoinBalance(stepAddress)
        assert btcBalance == startBTC

        // Send BTC to get MSC (and TMSC)
        txid = sendBitcoin(stepAddress, params.moneyManAddress, btcForMSC)
        generateBlock()
        tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Send to the actual destination
        Address fundedAddress = getNewAddress()
        send_MP(stepAddress, fundedAddress, MSC, requestedMSC)
        send_MP(stepAddress, fundedAddress, TMSC, requestedMSC)
        generateBlock()
        def remainingBTC = requestedBTC - getBitcoinBalance(fundedAddress)
        txid = sendBitcoin(stepAddress, fundedAddress, remainingBTC)
        generateBlock()
        tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Verify correct amounts received
        btcBalance = getBitcoinBalance(fundedAddress)
        BigDecimal mscBalance = getbalance_MP(fundedAddress, MSC).balance
        BigDecimal tmscBalance = getbalance_MP(fundedAddress, TMSC).balance

        assert btcBalance == requestedBTC
        assert mscBalance == requestedMSC
        assert tmscBalance == requestedMSC


        return fundedAddress
    }

    Address createFaucetAddress(String account, BigDecimal requestedBTC) {
        while (getBalance() < requestedBTC) {
            generateBlock()
        }
        // Create a BTC address to hold the requested BTC
        Address address = getAccountAddress(account)
        // and send it BTC
        Sha256Hash txid = sendToAddress(address, requestedBTC)
        generateBlock()
        def tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Make sure we got the correct amount of BTC
        BigDecimal btcBalance = getBalance(account)
        assert btcBalance == requestedBTC

        return address
    }

    Address createFaucetAddress(String account, BigDecimal requestedBTC, BigDecimal requestedMSC) {
        final MPNetworkParameters params = MPRegTestParams.get()  // Hardcoded for RegTest for now
        def btcForMSC = requestedMSC / 100
        def startBTC = requestedBTC + btcForMSC + stdTxFee

        // Generate blocks until we have the requested amount of BTC
        while (getBalance() < startBTC) {
            generateBlock()
        }

        // Create a BTC address to hold the requested BTC and MSC
        Address address = getAccountAddress(account)
        // and send it BTC
        Sha256Hash txid = sendToAddress(address, startBTC)

        generateBlock()
        def tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Make sure we got the correct amount of BTC
        BigDecimal btcBalance = getBalance(account)
        assert btcBalance == startBTC

        // Send BTC to get MSC (and TMSC)
        def amounts = [(params.moneyManAddress): btcForMSC,
                       (address): startBTC - btcForMSC ]
        txid = sendMany(account, amounts)

        generateBlock()
        tx = getTransaction(txid)
        assert tx.confirmations == 1

        // Verify correct amounts received
        btcBalance = getBalance(account)
        BigDecimal mscBalance = getbalance_MP(address, MSC).balance
        BigDecimal tmscBalance = getbalance_MP(address, TMSC).balance

        assert btcBalance == requestedBTC
        assert mscBalance == requestedMSC
        assert tmscBalance == requestedMSC

        return address
    }

    /**
     * Creates a raw transaction, sending {@code amount} from a single address to a destination, whereby no new change
     * address is created, and remaining amounts are returned to {@code fromAddress}.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param fromAddress The source to spent from
     * @param toAddress The destination
     * @param amount The amount
     * @return The hex-encoded raw transaction
     */
    String createRawTransaction(Address fromAddress, Address toAddress, BigDecimal amount) {
        def outputs = new HashMap<Address, BigDecimal>()
        outputs[toAddress] = amount
        return createRawTransaction(fromAddress, outputs)
    }

    /**
     * Creates a raw transaction, spending from a single address, whereby no new change address is created, and
     * remaining amounts are returned to {@code fromAddress}.
     *
     * Note: the transaction inputs are not signed, and the transaction is not stored in the wallet or transmitted to
     * the network.
     *
     * @param fromAddress The source to spent from
     * @param outputs The destinations and amounts to transfer
     * @return The hex-encoded raw transaction
     */
    String createRawTransaction(Address fromAddress, Map<Address, BigDecimal> outputs) {
        def amountIn = new BigDecimal(0)
        def amountOut = new BigDecimal(0)
        def inputs = new ArrayList<Map<String, Object>>()
        def unspentOutputs = listUnspent(0, 999999, [fromAddress])

        // Gather inputs
        for (unspentOutput in unspentOutputs) {
            def outpoint = new HashMap<String, Object>()
            def amountBTCd = unspentOutput["amount"] as Double
            amountIn += BigDecimal.valueOf(amountBTCd)
            outpoint["txid"] = unspentOutput["txid"]
            outpoint["vout"] = unspentOutput["vout"]
            inputs.add(outpoint)
        }

        // Sum outgoing amount
        for (entry in outputs.entrySet()) {
            amountOut += entry.value
        }

        // Calculate change
        def amountChange = amountIn - amountOut - stdTxFee
        if (amountChange > 0) {
            outputs[fromAddress] = amountChange
        }

        return createRawTransaction(inputs, outputs)
    }

    /**
     * Returns the Bitcoin balance of an address.
     *
     * @param address The address
     * @return The balance
     */
    BigDecimal getBitcoinBalance(Address address) {
        return getBitcoinBalance(address, 1, 99999)
    }

    /**
     * Returns the Bitcoin balance of an address where spendable outputs have at least {@code minConf} and not more
     * than {@code maxConf} confirmations.
     *
     * @param address The address
     * @param minConf Minimum amount of confirmations
     * @param maxConf Maximum amount of confirmations
     * @return The balance
     */
    BigDecimal getBitcoinBalance(Address address, Integer minConf, Integer maxConf) {
        def btcBalance = new BigDecimal(0)
        def unspentOutputs = (List<Map<String, Object>>) listUnspent(minConf, maxConf, [address])

        for (unspentOutput in unspentOutputs) {
            def balanceBTCd = unspentOutput["amount"] as Double
            btcBalance += BigDecimal.valueOf(balanceBTCd)
        }

        return btcBalance
    }

    /**
     * Sends BTC from an address to a destination, whereby no new change address is created, and any leftover is
     * returned to the sending address.
     *
     * @param fromAddress The source to spent from
     * @param toAddress   The destination address
     * @param amount      The amount to transfer
     * @return The transaction hash
     */
    Sha256Hash sendBitcoin(Address fromAddress, Address toAddress, BigDecimal amount) {
        def outputs = new HashMap<Address, BigDecimal>()
        outputs[toAddress] = amount
        return sendBitcoin(fromAddress, outputs)
    }

    /**
     * Sends BTC from an address to the destinations, whereby no new change address is created, and any leftover is
     * returned to the sending address.
     *
     * @param fromAddress The source to spent from
     * @param outputs     The destinations and amounts to transfer
     * @return The transaction hash
     */
    Sha256Hash sendBitcoin(Address fromAddress, Map<Address, BigDecimal> outputs) {
        def unsignedTxHex = createRawTransaction(fromAddress, outputs)
        def signingResult = signRawTransaction(unsignedTxHex)

        assert signingResult["complete"] == true

        def signedTxHex = signingResult["hex"] as String
        def txid = sendRawTransaction(signedTxHex)

        return txid
    }

    /**
     * Creates a smart property with fixed supply.
     *
     * @param address    The issuance address
     * @param ecosystem  The ecosystem to create the property in
     * @param type       The property type
     * @param amount     The number of units to create
     * @return The transaction hash
     */
    Sha256Hash createProperty(Address address, Ecosystem ecosystem, PropertyType type, Number amount) {
        return createProperty(address, ecosystem, type, amount, "SP");
    }

    /**
     * Creates a smart property with fixed supply.
     *
     * @param address    The issuance address
     * @param ecosystem  The ecosystem to create the property in
     * @param type       The property type
     * @param amount     The number of units to create
     * @param label      The label or title of the property
     * @return The transaction hash
     */
    Sha256Hash createProperty(Address address, Ecosystem ecosystem, PropertyType type, Number amount, String label) {
        def rawTxHex = createPropertyHex(ecosystem, type, 0, "", "", label, "", "", amount);
        def txid = sendrawtx_MP(address, rawTxHex)
        return txid
    }

    /**
     * Creates a hex-encoded raw transaction of type 50: "create property with fixed supply".
     */
    String createPropertyHex(Ecosystem ecosystem, PropertyType propertyType, Number previousPropertyId,
                             String category, String subCategory, String label, String website, String info,
                             Number amount) {
        def rawTxHex = String.format("00000032%02x%04x%08x%s00%s00%s00%s00%s00%016x",
                                     ecosystem.byteValue(),
                                     propertyType.intValue(),
                                     previousPropertyId.intValue(),
                                     toHexString(category),
                                     toHexString(subCategory),
                                     toHexString(label),
                                     toHexString(website),
                                     toHexString(info),
                                     amount.longValue())
        return rawTxHex
    }

    /**
     * Converts an UTF-8 encoded String into a hexadecimal string representation.
     *
     * @param str The string
     * @return The hexadecimal representation
     */
    String toHexString(String str) {
        def ba = str.getBytes("UTF-8")
        return toHexString(ba)
    }

    /**
     * Converts a byte array into a hexadecimal string representation.
     *
     * @param ba The byte array
     * @return The hexadecimal representation
     */
    String toHexString(byte[] ba) {
        StringBuilder str = new StringBuilder()
        for (int i = 0; i < ba.length; i++) {
            str.append(String.format("%x", ba[i]))
        }
        return str.toString()
    }

}