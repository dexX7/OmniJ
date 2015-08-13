package foundation.omni.rpc;

import com.msgilligan.bitcoin.rpc.BitcoinClient;
import com.msgilligan.bitcoin.rpc.JsonRPCException;
import com.msgilligan.bitcoin.rpc.RPCConfig;
import foundation.omni.CurrencyID;
import foundation.omni.Ecosystem;
import foundation.omni.PropertyType;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Sha256Hash;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure Java Bitcoin and Omni Core JSON-RPC client with camelCase method names.
 */
public class OmniClient extends BitcoinClient {

    public static Sha256Hash zeroHash = Sha256Hash.wrap("0000000000000000000000000000000000000000000000000000000000000000");
    private DecimalFormat jsonDecimalFormat;

    public OmniClient(RPCConfig config) throws IOException {
        this(config.getURI(), config.getUsername(), config.getPassword());
    }

    public OmniClient(URI server, String rpcuser, String rpcpassword) throws IOException {
        super(server, rpcuser, rpcpassword);
        // Create a DecimalFormat that fits our requirements
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        String pattern = "#,##0.0#";
        jsonDecimalFormat = new DecimalFormat(pattern, symbols);
        jsonDecimalFormat.setParseBigDecimal(true);
    }

    /**
     * Returns various state information of Omni Core and the Omni Layer protocol.
     *
     * @return An object with state information
     */
    public Map<String, Object> omniGetInfo() throws JsonRPCException, IOException {
        Map<String, Object> result = send("omni_getinfo", null);
        return result;
    }

    /**
     * Lists all currencies, smart properties and tokens.
     *
     * @return A list with short information
     */
    public List<SmartPropertyListInfo> omniListProperties() throws JsonRPCException, IOException {
        List<Map<String, Object>> result = send("omni_listproperties", null);

        List<SmartPropertyListInfo> propList = new ArrayList<SmartPropertyListInfo>();
        for (Map jsonProp : result) {
            // TODO: Should this mapping be done by Jackson?
            Number idnum = (Number) jsonProp.get("propertyid");
            CurrencyID id = new CurrencyID(idnum.longValue());
            String name = (String) jsonProp.get("name");
            String category = (String) jsonProp.get("category");
            String subCategory = (String) jsonProp.get("subcategory");
            String data = (String) jsonProp.get("data");
            String url = (String) jsonProp.get("url");
            Boolean divisible = (Boolean) jsonProp.get("divisible");
            SmartPropertyListInfo prop = new SmartPropertyListInfo(id,
                    name,
                    category,
                    subCategory,
                    data,
                    url,
                    divisible);
            propList.add(prop);
        }
        return propList;
    }

    /**
     * Returns information about the given currency, property, or token.
     *
     * @param currency The identifier to look up
     * @return An object with detailed information
     */
    public Map<String, Object> omniGetProperty(CurrencyID currency) throws JsonRPCException, IOException {
        List<Object> params = createParamList(currency.longValue());
        Map<String, Object> result = send("omni_getproperty", params);
        return result;
    }

    /**
     * Returns information about a crowdsale.
     *
     * @param currency The identifier of the crowdsale
     * @return An object with detailed information
     */
    public Map<String, Object> omniGetCrowdsale(CurrencyID currency) throws JsonRPCException, IOException {
        List<Object> params = createParamList(currency.longValue());
        Map<String, Object> result = send("omni_getcrowdsale", params);
        return result;
    }

    /**
     * Lists currently active offers on the distributed BTC/MSC exchange.
     *
     * @return A list with information about the active offers
     */
    public List<Map<String, Object>> omniGetActiveDExSells() throws JsonRPCException, IOException {
        List<Map<String, Object>> result = send("omni_getactivedexsells", null);
        return result;
    }

    /**
     * Returns the balance for a given address and property.
     *
     * @param address  The address to look up
     * @param currency The identifier of the token to look up
     * @return The available and reserved balance
     */
    public MPBalanceEntry omniGetBalance(Address address, CurrencyID currency)
            throws JsonRPCException, IOException, ParseException {
        List<Object> params = createParamList(address.toString(), currency.longValue());
        Map<String, String> result = send("omni_getbalance", params);
        BigDecimal balanceBTC = (BigDecimal) jsonDecimalFormat.parse(result.get("balance"));
        BigDecimal reservedBTC = (BigDecimal) jsonDecimalFormat.parse(result.get("reserved"));
        MPBalanceEntry entry = new MPBalanceEntry(address, balanceBTC, reservedBTC);
        return entry;
    }

    /**
     * Returns a list of balances for a given identifier.
     *
     * @param currency The identifier of the token to look up
     * @return A list containing addresses, and the associated available and reserved balances
     */
    public List<MPBalanceEntry> omniGetAllBalancesForId(CurrencyID currency)
            throws JsonRPCException, IOException, ParseException, AddressFormatException {
        List<Object> params = createParamList(currency.longValue());
        List<Map<String, Object>> untypedBalances = send("omni_getallbalancesforid", params);
        List<MPBalanceEntry> balances = new ArrayList<MPBalanceEntry>(untypedBalances.size());
        for (Map map : untypedBalances) {
            // TODO: Should this mapping be done by Jackson?
            BigDecimal balance;
            BigDecimal reserved;
            String addressString = (String) map.get("address");
            Address address = new Address(null, addressString);
            Object balanceJson = map.get("balance");
            Object reservedJson = map.get("reserved");
            /* Assume that if balanceJson field is of type String, so is reserved */
            /* The RPCs have been changing here, but currently they should be using Strings */
            if (balanceJson instanceof String) {
                balance = (BigDecimal) jsonDecimalFormat.parse((String) balanceJson);
                reserved = (BigDecimal) jsonDecimalFormat.parse((String) reservedJson);
            } else if (balanceJson instanceof Integer) {
                balance = new BigDecimal((Integer) balanceJson);
                reserved = new BigDecimal((Integer) reservedJson);

            } else {
                throw new RuntimeException("unexpected data type");
            }
            MPBalanceEntry balanceEntry = new MPBalanceEntry(address, balance, reserved);
            balances.add(balanceEntry);
        }
        return balances;
    }

    /**
     * Returns information about an Omni Layer transaction.
     *
     * @param txid The hash of the transaction to look up
     * @return Information about the transaction
     */
    public Map<String, Object> omniGetTransaction(Sha256Hash txid) throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString());
        Map<String, Object> transaction = send("omni_gettransaction", params);
        return transaction;
    }

    /**
     * Broadcasts a raw Omni Layer transaction.
     *
     * @param fromAddress The address to send from
     * @param rawTxHex    The hex-encoded raw transaction
     * @return The hash of the transaction
     */
    public Sha256Hash omniSendRawTx(Address fromAddress, String rawTxHex) throws JsonRPCException, IOException {
        return omniSendRawTx(fromAddress, rawTxHex, null);
    }

    /**
     * Broadcasts a raw Omni Layer transaction with reference address.
     *
     * @param fromAddress      The address to send from
     * @param rawTxHex         The hex-encoded raw transaction
     * @param referenceAddress The reference address
     * @return The hash of the transaction
     */
    public Sha256Hash omniSendRawTx(Address fromAddress, String rawTxHex, Address referenceAddress)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), rawTxHex);
        if (referenceAddress != null) {
            params.add(referenceAddress.toString());
        }
        String txid = send("omni_sendrawtx", params);
        return Sha256Hash.wrap(txid);
    }

    /**
     * Creates and broadcasts a "simple send" transaction.
     *
     * @param fromAddress The address to spent from
     * @param toAddress   The address to send to
     * @param currency    The identifier of the token to transfer
     * @param amount      The amount to transfer
     * @return The hash of the transaction
     */
    public Sha256Hash omniSend(Address fromAddress, Address toAddress, CurrencyID currency, BigDecimal amount)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), toAddress.toString(), currency.longValue(),
                                              amount.toPlainString());
        String txid = send("omni_send", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Creates and broadcasts a "send to owners" transaction.
     *
     * @param fromAddress The address to spent from
     * @param currency    The identifier of the token to distribute
     * @param amount      The amount to distribute
     * @return The hash of the transaction
     */
    public Sha256Hash omniSendSTO(Address fromAddress, CurrencyID currency, BigDecimal amount)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), currency.longValue(), amount.toPlainString());
        String txid = send("omni_sendsto", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Creates and broadcasts a "send all" transaction.
     *
     * @param fromAddress The address to spent from
     * @param toAddress   The address to send to
     * @param ecosystem   The ecosystem of the tokens to send
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendAll(Address fromAddress, Address toAddress, Ecosystem ecosystem)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), toAddress.toString(), ecosystem.byteValue());
        String txid = send("omni_sendall", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Creates an offer on the traditional distributed exchange.
     *
     * @param fromAddress   The address
     * @param currencyId    The identifier of the currency for sale
     * @param amountForSale The amount of currency (BigDecimal coins)
     * @param amountDesired The amount of desired Bitcoin (in BTC)
     * @param paymentWindow The payment window measured in blocks
     * @param commitmentFee The minimum transaction fee required to be paid as commitment when accepting this offer
     * @param action        The action applied to the offer (1 = new, 2 = update, 3 = cancel)
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendDExSell(Address fromAddress, CurrencyID currencyId, BigDecimal amountForSale,
                                      BigDecimal amountDesired, Byte paymentWindow, BigDecimal commitmentFee,
                                      Byte action)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), currencyId.longValue(),
                                              amountForSale.toPlainString(), amountDesired.toPlainString(),
                                              paymentWindow, commitmentFee.toPlainString(), action);
        String txid = send("omni_senddexsell", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * @param fromAddress The address to send from
     * @param toAddress   The address of the seller
     * @param currencyId  The identifier of the token to purchase
     * @param amount      The amount to accept
     * @param override    Override minimum accept fee and payment window checks (use with caution!)
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendDExAccept(Address fromAddress, Address toAddress, CurrencyID currencyId,
                                        BigDecimal amount, Boolean override)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), toAddress.toString(), currencyId.longValue(),
                                              amount.toPlainString(), override);
        String txid = send("omni_senddexaccept", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Place a trade offer on the distributed token exchange.
     *
     * @param fromAddress     The address to trade with
     * @param propertyForSale The identifier of the tokens to list for sale
     * @param amountForSale   The amount of tokens to list for sale
     * @param propertyDesired The identifier of the tokens desired in exchange
     * @param amountDesired   The amount of tokens desired in exchange
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendTrade(Address fromAddress, CurrencyID propertyForSale, BigDecimal amountForSale,
                                    CurrencyID propertyDesired, BigDecimal amountDesired)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), propertyForSale.longValue(),
                                              amountForSale.toPlainString(), propertyDesired.longValue(),
                                              amountDesired.toPlainString());
        String txid = send("omni_sendtrade", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Cancel offers on the distributed token exchange with the specified price.
     *
     * @param fromAddress     The address to trade with
     * @param propertyForSale The identifier of the tokens to list for sale
     * @param amountForSale   The amount of tokens to list for sale
     * @param propertyDesired The identifier of the tokens desired in exchange
     * @param amountDesired   The amount of tokens desired in exchange
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendCancelTradesByPrice(Address fromAddress, CurrencyID propertyForSale,
                                                  BigDecimal amountForSale, CurrencyID propertyDesired,
                                                  BigDecimal amountDesired)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), propertyForSale.longValue(),
                                              amountForSale.toPlainString(), propertyDesired.longValue(),
                                              amountDesired.toPlainString());
        String txid = send("omni_sendcanceltradesbyprice", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Cancel all offers on the distributed token exchange with the given currency pair.
     *
     * @param fromAddress     The address to trade with
     * @param propertyForSale The identifier of the tokens listed for sale
     * @param propertyDesired The identifier of the tokens desired in exchange
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendCancelTradesByPair(Address fromAddress, CurrencyID propertyForSale,
                                                 CurrencyID propertyDesired)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), propertyForSale.longValue(),
                                              propertyDesired.longValue());
        String txid = send("omni_sendcanceltradesbypair", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Cancel all offers on the distributed token exchange with the given currency pair.
     *
     * @param fromAddress The address to trade with
     * @param ecosystem   The ecosystem of the offers to cancel: (1) main, (2) test
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendCancelAllTrades(Address fromAddress, Ecosystem ecosystem)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), ecosystem.byteValue());
        String txid = send("omni_sendcancelalltrades", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Create new tokens with fixed supply.
     *
     * @param fromAddress  The address to send from
     * @param ecosystem    The ecosystem to create the tokens in
     * @param propertyType The type of the tokens to create
     * @param previousId   An identifier of a predecessor token (0 for new tokens)
     * @param category     A category for the new tokens (can be "")
     * @param subCategory  A subcategory for the new tokens (can be "")
     * @param name         The name of the new tokens to create
     * @param url          An URL for further information about the new tokens (can be "")
     * @param data         A description for the new tokens (can be "")
     * @param amount       The number of tokens to create
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendIssuanceFixed(Address fromAddress, Ecosystem ecosystem, PropertyType propertyType,
                                            CurrencyID previousId, String category, String subCategory, String name,
                                            String url, String data, BigDecimal amount)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), ecosystem.byteValue(), propertyType.intValue(),
                                              previousId.longValue(), category, subCategory, name, url, data,
                                              amount.toPlainString());
        String txid = send("omni_sendissuancefixed", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Create new tokens as crowdsale.
     *
     * @param fromAddress     The address to send from
     * @param ecosystem       The ecosystem to create the tokens in
     * @param propertyType    The type of the tokens to create
     * @param previousId      An identifier of a predecessor token (0 for new tokens)
     * @param category        A category for the new tokens (can be "")
     * @param subCategory     A subcategory for the new tokens (can be "")
     * @param name            The name of the new tokens to create
     * @param url             An URL for further information about the new tokens (can be "")
     * @param data            A description for the new tokens (can be "")
     * @param propertyDesired the identifier of a token eligible to participate in the crowdsale
     * @param tokensPerUnit   the amount of tokens granted per unit invested in the crowdsale
     * @param deadline        the deadline of the crowdsale as Unix timestamp
     * @param earlyBirdBonus  an early bird bonus for participants in percent per week
     * @param issuerBonus     a percentage of tokens that will be granted to the issuer
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendIssuanceCrowdsale(Address fromAddress, Ecosystem ecosystem, PropertyType propertyType,
                                                CurrencyID previousId, String category, String subCategory, String name,
                                                String url, String data, CurrencyID propertyDesired,
                                                BigDecimal tokensPerUnit, Long deadline, Byte earlyBirdBonus,
                                                Byte issuerBonus)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), ecosystem.byteValue(), propertyType.intValue(),
                                              previousId.longValue(), category, subCategory, name, url, data,
                                              propertyDesired.longValue(), tokensPerUnit.toPlainString(), deadline,
                                              earlyBirdBonus, issuerBonus);
        String txid = send("omni_sendissuancecrowdsale", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Manually close a crowdsale.
     *
     * @param fromAddress The address associated with the crowdsale to close
     * @param propertyId  The identifier of the crowdsale to close
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendCloseCrowdsale(Address fromAddress, CurrencyID propertyId)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), propertyId.longValue());
        String txid = send("omni_sendclosecrowdsale", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Create new tokens with manageable supply.
     *
     * @param fromAddress  The address to send from
     * @param ecosystem    The ecosystem to create the tokens in
     * @param propertyType The type of the tokens to create
     * @param previousId   An identifier of a predecessor token (0 for new tokens)
     * @param category     A category for the new tokens (can be "")
     * @param subCategory  A subcategory for the new tokens (can be "")
     * @param name         The name of the new tokens to create
     * @param url          An URL for further information about the new tokens (can be "")
     * @param data         A description for the new tokens (can be "")
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendIssuanceManaged(Address fromAddress, Ecosystem ecosystem, PropertyType propertyType,
                                              CurrencyID previousId, String category, String subCategory, String name,
                                              String url, String data)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), ecosystem.byteValue(), propertyType.intValue(),
                                              previousId.longValue(), category, subCategory, name, url, data);
        String txid = send("omni_sendissuancemanaged", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Issue or grant new units of managed tokens.
     *
     * @param fromAddress The address to send from
     * @param toAddress   The receiver of the tokens
     * @param propertyId  The identifier of the tokens to grant
     * @param amount      The amount of tokens to create
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendGrant(Address fromAddress, Address toAddress, CurrencyID propertyId, BigDecimal amount)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), toAddress.toString(), propertyId.longValue(),
                                              amount.toPlainString());
        String txid = send("omni_sendgrant", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Revoke units of managed tokens.
     *
     * @param fromAddress The address to revoke the tokens from
     * @param propertyId  The identifier of the tokens to revoke
     * @param amount      The amount of tokens to revoke
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendRevoke(Address fromAddress, CurrencyID propertyId, BigDecimal amount)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), propertyId.longValue(), amount.toPlainString());
        String txid = send("omni_sendrevoke", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Change the issuer on record of the given tokens.
     *
     * @param fromAddress The address associated with the tokens
     * @param toAddress   The address to transfer administrative control to
     * @param propertyId  The identifier of the tokens
     * @return The hash of the transaction
     * @since Omni Core 0.0.10
     */
    public Sha256Hash omniSendChangeIssuer(Address fromAddress, Address toAddress, CurrencyID propertyId)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(fromAddress.toString(), toAddress.toString(), propertyId.longValue());
        String txid = send("omni_sendchangeissuer", params);
        Sha256Hash hash = Sha256Hash.wrap(txid);
        return hash;
    }

    /**
     * Returns information about an order on the distributed token exchange.
     *
     * @param txid The transaction hash of the order to look up
     * @return Information about the order, trade, and order matches
     * @since Omni Core 0.0.10
     */
    public Map<String, Object> omniGetTrade(Sha256Hash txid) throws JsonRPCException, IOException {
        List<Object> params = createParamList(txid.toString());
        Map<String, Object> trade = send("omni_gettrade", params);
        return trade;
    }

    /**
     * Lists orders on the distributed token exchange with the given token for sale.
     *
     * @param propertyForSale The identifier of the token for sale, used as filter
     * @return A list of orders
     * @since Omni Core 0.0.10
     */
    public List<Map<String, Object>> omniGetOrderbook(CurrencyID propertyForSale) throws JsonRPCException, IOException {
        List<Object> params = createParamList(propertyForSale.longValue());
        List<Map<String, Object>> orders = send("omni_getorderbook", params);
        return orders;
    }

    /**
     * Lists orders on the distributed token exchange with the given token for sale, and token desired.
     *
     * @param propertyForSale The identifier of the token for sale, used as filter
     * @param propertyDesired The identifier of the token desired, used as filter
     * @return A list of orders
     * @since Omni Core 0.0.10
     */
    public List<Map<String, Object>> omniGetOrderbook(CurrencyID propertyForSale, CurrencyID propertyDesired)
            throws JsonRPCException, IOException {
        List<Object> params = createParamList(propertyForSale.longValue(), propertyDesired.longValue());
        List<Map<String, Object>> orders = send("omni_getorderbook", params);
        return orders;
    }

}
