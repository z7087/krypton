package me.steinborn.krypton.mod.shared;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import me.steinborn.krypton.mod.shared.network.util.VarIntUtil;
import me.steinborn.krypton.mod.shared.network.util.VarLongUtil;
import me.z7087.final2constant.Constant;
import me.z7087.final2constant.DynamicConstant;
import me.z7087.final2constant.util.JavaHelper;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

public abstract class Config {
    public static final File PATH_CONFIG = new File(FabricLoader.getInstance().getConfigDir().toFile(), KryptonSharedInitializer.MOD_ID + ".json");
    private static final Logger LOGGER = LogManager.getLogger(Config.class);
    public static final Gson GSON;

    static {
        GSON = new GsonBuilder()
                .registerTypeAdapter(Config.class, new ConfigTypeAdapter())
                .setPrettyPrinting()
                .create();
    }

    private Config() {
    }

    private static final MethodHandle CONSTRUCTOR;

    static {
        final String[] immutableNames, immutableDescriptors;
        try {
            Config configEmptyImpl = Constant.factory.ofEmptyAbstractImplInstance(
                    MethodHandles.lookup(),
                    Config.class
            );
            final String[][] immutableNamesAndDescriptors = JavaHelper.getNamesAndDescriptors(
                    MethodHandles.lookup(),
                    (Supplier<DynamicConstant<Boolean>> & Serializable) configEmptyImpl::permitOversizedPackets,
                    (Supplier<DynamicConstant<Boolean>> & Serializable) configEmptyImpl::useLSHL,
                    (Supplier<DynamicConstant<VarIntUtil.VarIntProvider>> & Serializable) configEmptyImpl::varIntProvider,
            (Supplier<DynamicConstant<VarLongUtil.VarLongProvider>> & Serializable) configEmptyImpl::varLongProvider
            );
            immutableNames = immutableNamesAndDescriptors[0];
            immutableDescriptors = immutableNamesAndDescriptors[1];
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
        CONSTRUCTOR = Constant.factory.ofRecordConstructor(
                MethodHandles.lookup(),
                Config.class,
                false,
                immutableNames,
                immutableDescriptors,
                null,
                null,
                true,
                false
        );
    }

    public static Config createInstance() {
        final DynamicConstant<Boolean> permitOversizedPackets = Constant.factory.ofMutable(false);
        final DynamicConstant<Boolean> useLSHL = Constant.factory.ofMutable(false);
        final DynamicConstant<VarIntUtil.VarIntProvider> varIntProvider = Constant.factory.ofMutable(VarIntUtil.VarIntProvider.getDefaultProvider());
        final DynamicConstant<VarLongUtil.VarLongProvider> varLongProvider = Constant.factory.ofMutable(VarLongUtil.VarLongProvider.getDefaultProvider());

        try {
            return (Config) CONSTRUCTOR.invokeExact(
                    permitOversizedPackets,
                    useLSHL,
                    varIntProvider,
                    varLongProvider
            );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    abstract DynamicConstant<Boolean> permitOversizedPackets();
    abstract DynamicConstant<Boolean> useLSHL();
    abstract DynamicConstant<VarIntUtil.VarIntProvider> varIntProvider();
    abstract DynamicConstant<VarLongUtil.VarLongProvider> varLongProvider();


    public static Config createDefaultConfig() {
        return Config.createInstance();
    }

    private static void mkdirs() {
        File parent = PATH_CONFIG.getParentFile();
        if (!parent.exists() || !parent.isDirectory())
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
    }

    public static Config loadFromFile() {
        if (!PATH_CONFIG.exists() || !PATH_CONFIG.isFile()) {
            return null;
        }
        Reader reader = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            reader = new FileReader(PATH_CONFIG);
            return GSON.fromJson(reader, Config.class);
        } catch (FileNotFoundException ignored) {
            return null;
        } catch (Exception e) {
            LOGGER.warn("{}", e.getMessage(), e);
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
        }

    }

    public static void saveToFile(Config config) throws IOException {
        mkdirs();
        Writer writer = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            writer = new FileWriter(PATH_CONFIG);
            GSON.toJson(config, Config.class, writer);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public final boolean isPermitOversizedPackets() {
        return permitOversizedPackets().orElseThrow();
    }

    public final void setPermitOversizedPackets(boolean permitOversizedPackets) {
        permitOversizedPackets().set(permitOversizedPackets);
    }

    public final boolean isUseLSHL() {
        return useLSHL().orElseThrow();
    }

    public final void setUseLSHL(boolean useLSHL) {
        useLSHL().set(useLSHL);
    }

    public final VarIntUtil.VarIntProvider getVarIntProvider() {
        return varIntProvider().orElseThrow();
    }

    public final void setVarIntProvider(VarIntUtil.VarIntProvider provider) {
        varIntProvider().set(provider);
    }

    public final VarLongUtil.VarLongProvider getVarLongProvider() {
        return varLongProvider().orElseThrow();
    }

    public final void setVarLongProvider(VarLongUtil.VarLongProvider provider) {
        varLongProvider().set(provider);
    }

    private static final class ConfigTypeAdapter extends TypeAdapter<Config> {
        @Override
        public void write(JsonWriter out, Config config) throws IOException {
            out.beginObject();
            out.name("permit-oversized-packets").value(config.isPermitOversizedPackets());
            out.name("use-LSHL").value(config.isPermitOversizedPackets());
            out.name("varint-provider").value(config.getVarIntProvider().getName());
            out.name("varlong-provider").value(config.getVarLongProvider().getName());
            out.endObject();
        }

        @Override
        public Config read(JsonReader in) throws IOException {
            final Config config = Config.createInstance();
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "permit-oversized-packets": {
                        config.setPermitOversizedPackets(in.nextBoolean());
                        break;
                    }
                    case "use-LSHL": {
                        config.setUseLSHL(in.nextBoolean());
                        break;
                    }
                    case "varint-provider": {
                        config.setVarIntProvider(VarIntUtil.VarIntProvider.forName(in.nextString()));
                        break;
                    }
                    case "varlong-provider": {
                        config.setVarLongProvider(VarLongUtil.VarLongProvider.forName(in.nextString()));
                        break;
                    }
                    default: {
                        in.skipValue();
                    }
                }
            }
            in.endObject();
            return config;
        }
    }
}
