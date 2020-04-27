package org.tron.core.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.protobuf.ByteString;
import org.spongycastle.util.encoders.Hex;
import org.tron.common.utils.ByteArray;
import org.tron.common.utils.Hash;
import org.tron.protos.contract.SmartContractOuterClass;
import stest.tron.wallet.common.client.WalletClient;

import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BuildContract {

    public static SmartContractOuterClass.CreateSmartContract deployContractMsg(
            String contractName,
            byte[] address,
            String ABI,
            String code,
            long value,
            long consumeUserResourcePercent,
            long originEnergyLimit,
            long tokenValue,
            String tokenId,
            String libraryAddressPair,
            String compilerVersion) {
        SmartContractOuterClass.SmartContract.ABI abi = jsonStr2ABI(ABI);
        if (abi == null) {
            System.out.println("abi is null");
            return null;
        }

        SmartContractOuterClass.SmartContract.Builder builder = SmartContractOuterClass.SmartContract.newBuilder();
        builder.setName(contractName);
        builder.setOriginAddress(ByteString.copyFrom(address));
        builder.setAbi(abi);
        builder
                .setConsumeUserResourcePercent(consumeUserResourcePercent)
                .setOriginEnergyLimit(originEnergyLimit);

        if (value != 0) {

            builder.setCallValue(value);
        }
        byte[] byteCode;
        if (null != libraryAddressPair) {
            byteCode = replaceLibraryAddress(code, libraryAddressPair, compilerVersion);
        } else {
            byteCode = Hex.decode(code);
        }

        builder.setBytecode(ByteString.copyFrom(byteCode));
        SmartContractOuterClass.CreateSmartContract.Builder createSmartContractBuilder = SmartContractOuterClass.CreateSmartContract.newBuilder();
        createSmartContractBuilder
                .setOwnerAddress(ByteString.copyFrom(address))
                .setNewContract(builder.build());
        if (tokenId != null && !tokenId.equalsIgnoreCase("") && !tokenId.equalsIgnoreCase("#")) {
            createSmartContractBuilder.setCallTokenValue(tokenValue).setTokenId(Long.parseLong(tokenId));
        }
        return createSmartContractBuilder.build();
    }

    private static byte[] replaceLibraryAddress(String code, String libraryAddressPair,
                                                String compilerVersion) {

        String[] libraryAddressList = libraryAddressPair.split("[,]");

        for (int i = 0; i < libraryAddressList.length; i++) {
            String cur = libraryAddressList[i];

            int lastPosition = cur.lastIndexOf(":");
            if (-1 == lastPosition) {
                throw new RuntimeException("libraryAddress delimit by ':'");
            }
            String libraryName = cur.substring(0, lastPosition);
            String addr = cur.substring(lastPosition + 1);
            String libraryAddressHex;
            try {
                libraryAddressHex = (new String(Hex.encode(WalletClient.decodeFromBase58Check(addr)),
                        "US-ASCII")).substring(2);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e); // now ignore
            }

            String beReplaced;
            if (compilerVersion == null) {
                // old version
                String repeated = new String(
                        new char[40 - libraryName.length() - 2])
                        .replace("\0", "_");
                beReplaced = "__" + libraryName + repeated;
            } else if (compilerVersion.equalsIgnoreCase("v5")) {
                // 0.5.4 version
                String libraryNameKeccak256 =
                        ByteArray.toHexString(
                                Hash.sha3(ByteArray.fromString(libraryName)))
                                .substring(0, 34);
                beReplaced = "__\\$" + libraryNameKeccak256 + "\\$__";
            } else {
                throw new RuntimeException("unknown compiler version.");
            }

            Matcher m = Pattern.compile(beReplaced).matcher(code);
            code = m.replaceAll(libraryAddressHex);
        }

        return Hex.decode(code);
    }

    private static SmartContractOuterClass.SmartContract.ABI jsonStr2ABI(String jsonStr) {
        if (jsonStr == null) {
            return null;
        }

        JsonParser jsonParser = new JsonParser();
        JsonElement jsonElementRoot = jsonParser.parse(jsonStr);
        JsonArray jsonRoot = jsonElementRoot.getAsJsonArray();
        SmartContractOuterClass.SmartContract.ABI.Builder abiBuilder = SmartContractOuterClass.SmartContract.ABI.newBuilder();
        for (int index = 0; index < jsonRoot.size(); index++) {
            JsonElement abiItem = jsonRoot.get(index);
            boolean anonymous =
                    abiItem.getAsJsonObject().get("anonymous") != null
                            ? abiItem.getAsJsonObject().get("anonymous")
                            .getAsBoolean()
                            : false;
            boolean constant =
                    abiItem.getAsJsonObject().get("constant") != null
                            ? abiItem.getAsJsonObject().get("constant")
                            .getAsBoolean()
                            : false;
            String name =
                    abiItem.getAsJsonObject().get("name") != null
                            ? abiItem.getAsJsonObject().get("name").getAsString()
                            : null;
            JsonArray inputs =
                    abiItem.getAsJsonObject().get("inputs") != null
                            ? abiItem.getAsJsonObject().get("inputs")
                            .getAsJsonArray()
                            : null;
            JsonArray outputs =
                    abiItem.getAsJsonObject().get("outputs") != null
                            ? abiItem.getAsJsonObject().get("outputs")
                            .getAsJsonArray()
                            : null;
            String type =
                    abiItem.getAsJsonObject().get("type") != null
                            ? abiItem.getAsJsonObject().get("type").getAsString()
                            : null;
            boolean payable =
                    abiItem.getAsJsonObject().get("payable") != null
                            ? abiItem.getAsJsonObject().get("payable")
                            .getAsBoolean()
                            : false;
            String stateMutability =
                    abiItem.getAsJsonObject().get("stateMutability") != null
                            ? abiItem.getAsJsonObject().get("stateMutability")
                            .getAsString()
                            : null;
            if (type == null) {
                System.out.println("No type!");
                return null;
            }
            if (!type.equalsIgnoreCase("fallback") && null == inputs) {
                System.out.println("No inputs!");
                return null;
            }

            SmartContractOuterClass.SmartContract.ABI.Entry.Builder entryBuilder = SmartContractOuterClass.SmartContract.ABI.Entry.newBuilder();
            entryBuilder.setAnonymous(anonymous);
            entryBuilder.setConstant(constant);
            if (name != null) {
                entryBuilder.setName(name);
            }

            /* { inputs : optional } since fallback function not requires inputs*/
            if (null != inputs) {
                for (int j = 0; j < inputs.size(); j++) {
                    JsonElement inputItem = inputs.get(j);
                    if (inputItem.getAsJsonObject().get("name") == null
                            || inputItem.getAsJsonObject().get("type") == null) {
                        System.out.println("Input argument invalid due to no name or no type!");
                        return null;
                    }
                    String inputName = inputItem.getAsJsonObject().get("name").getAsString();
                    String inputType = inputItem.getAsJsonObject().get("type").getAsString();
                    Boolean inputIndexed = false;
                    if (inputItem.getAsJsonObject().get("indexed") != null) {
                        inputIndexed =
                                Boolean.valueOf(
                                        inputItem.getAsJsonObject().get("indexed")
                                                .getAsString());
                    }
                    SmartContractOuterClass.SmartContract.ABI.Entry.Param.Builder paramBuilder =
                            SmartContractOuterClass.SmartContract.ABI.Entry.Param.newBuilder();
                    paramBuilder.setIndexed(inputIndexed);
                    paramBuilder.setName(inputName);
                    paramBuilder.setType(inputType);
                    entryBuilder.addInputs(paramBuilder.build());
                }
            }

            /* { outputs : optional } */
            if (outputs != null) {
                for (int k = 0; k < outputs.size(); k++) {
                    JsonElement outputItem = outputs.get(k);
                    if (outputItem.getAsJsonObject().get("name") == null
                            || outputItem.getAsJsonObject().get("type") == null) {
                        System.out.println("Output argument invalid due to no name or no type!");
                        return null;
                    }
                    String outputName = outputItem.getAsJsonObject().get("name").getAsString();
                    String outputType = outputItem.getAsJsonObject().get("type").getAsString();
                    Boolean outputIndexed = false;
                    if (outputItem.getAsJsonObject().get("indexed") != null) {
                        outputIndexed =
                                Boolean.valueOf(
                                        outputItem.getAsJsonObject().get("indexed")
                                                .getAsString());
                    }
                    SmartContractOuterClass.SmartContract.ABI.Entry.Param.Builder paramBuilder =
                            SmartContractOuterClass.SmartContract.ABI.Entry.Param.newBuilder();
                    paramBuilder.setIndexed(outputIndexed);
                    paramBuilder.setName(outputName);
                    paramBuilder.setType(outputType);
                    entryBuilder.addOutputs(paramBuilder.build());
                }
            }

            entryBuilder.setType(getEntryType(type));
            entryBuilder.setPayable(payable);
            if (stateMutability != null) {
                entryBuilder.setStateMutability(
                        getStateMutability(stateMutability));
            }

            abiBuilder.addEntrys(entryBuilder.build());
        }

        return abiBuilder.build();
    }

    private static SmartContractOuterClass.SmartContract.ABI.Entry.EntryType getEntryType(String type) {
        switch (type) {
            case "constructor":
                return SmartContractOuterClass.SmartContract.ABI.Entry.EntryType.Constructor;
            case "function":
                return SmartContractOuterClass.SmartContract.ABI.Entry.EntryType.Function;
            case "event":
                return SmartContractOuterClass.SmartContract.ABI.Entry.EntryType.Event;
            case "fallback":
                return SmartContractOuterClass.SmartContract.ABI.Entry.EntryType.Fallback;
            default:
                return SmartContractOuterClass.SmartContract.ABI.Entry.EntryType.UNRECOGNIZED;
        }
    }


    public static SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType getStateMutability(
            String stateMutability) {
        switch (stateMutability) {
            case "pure":
                return SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType.Pure;
            case "view":
                return SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType.View;
            case "nonpayable":
                return SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType.Nonpayable;
            case "payable":
                return SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType.Payable;
            default:
                return SmartContractOuterClass.SmartContract.ABI.Entry.StateMutabilityType.UNRECOGNIZED;
        }
    }

}
