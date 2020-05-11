package uk.ac.ox.well.cortexjdk.utils.packageutils;

import com.google.common.base.Joiner;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;
import uk.ac.ox.well.cortexjdk.Main;
import uk.ac.ox.well.cortexjdk.commands.Module;
import uk.ac.ox.well.cortexjdk.utils.arguments.Output;
import uk.ac.ox.well.cortexjdk.utils.performance.PerformanceUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.annotation.Annotation;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Instantiates a module and passes it command-line arguments.
 */
public class Dispatch {
    /**
     * Private constructor - this class cannot be instantiated!
     */
    private Dispatch() {}

    /**
     * Main method for instantiating CortexJDK modules
     *
     * @param moduleName  the name of the module
     * @param moduleArgs  the arguments for the module
     */
    @SuppressWarnings("unchecked")
    public static void main(String moduleName, String[] moduleArgs) {
        try {
            Class<? extends Module> module = (Class<? extends Module>) Class.forName(moduleName);

            Main.getLogger().info("{}", getBanner());
            Main.getLogger().info("java -Xmx{}g -jar corticall.jar {} {}", (Runtime.getRuntime().maxMemory()/(1024*1024))/1024, module.getSimpleName(), Joiner.on(" ").join(moduleArgs));
            Main.getLogger().info("");

            disableIllegalAccessWarning();

            Module instance = module.newInstance();
            instance.args = moduleArgs;

            Field[] instanceFields = instance.getClass().getDeclaredFields();
            Field[] superFields = instance.getClass().getSuperclass().getDeclaredFields();

            List<Field> fields = new ArrayList<>();
            fields.addAll(Arrays.asList(instanceFields));
            fields.addAll(Arrays.asList(superFields));

            //System.err.close();

            instance.init();

            Date startTime = new Date();

            instance.execute();

            for (Field field : fields) {
                for (Annotation annotation : field.getDeclaredAnnotations()) {
                    if (annotation.annotationType().equals(Output.class)) {
                        if (field.get(instance) instanceof PrintStream) {
                            ((PrintStream) field.get(instance)).close();
                        }
                    }
                }
            }

            Date elapsedTime = new Date((new Date()).getTime() - startTime.getTime());

            //try {
            //    System.setErr(new PrintStream(new FileOutputStream("/dev/stderr")));
            //} catch (FileNotFoundException e) {}

            Main.getLogger().info("");
            Main.getLogger().info("Complete. (time) {}; (mem) {}",
                                  DurationFormatUtils.formatDurationHMS(elapsedTime.getTime()),
                                  PerformanceUtils.getCompactMemoryUsageStats() );
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static String getFullCommand(String[] moduleArgs, Module instance, List<String> defaultArgs) {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        List<String> arguments = runtimeMxBean.getInputArguments();
        String jvmArgs = Joiner.on(" ").join(arguments);
        String jar = System.getProperty("sun.java.command").split("\\s+")[0];
        String cortexJdkCmd = instance.getClass().getSimpleName();

        List<String> pieces = new ArrayList<>();
        pieces.add(jar);
        pieces.add(cortexJdkCmd);
        pieces.addAll(Arrays.asList(moduleArgs));
        pieces.addAll(defaultArgs);

        /*
        List<String> modPieces = new ArrayList<>();

        for (String piece : pieces) {
            File f = new File(piece);
            if (f.isFile()) {
                modPieces.add(f.getAbsolutePath());
            } else {
                modPieces.add(piece);
            }
        }
        */

        return Joiner.on(" ").join("java", jvmArgs, "-jar", Joiner.on(" ").join(pieces)).replaceAll("\\s+", " ");
    }

    private static String getBanner() {
        Properties prop = Main.getBuildProperties();

        if (prop != null) {
            String version = prop.get("major.version") + "." + prop.get("minor.version") + "-" + prop.get("git.version");
            String gitDate = (String) prop.get("git.date");
            String buildDate = (String) prop.get("build.date");
            String dates = "(repo) " + gitDate + "; (build) " + buildDate;
            return Main.progName + " " + version + "; " + dates;
        } else {
            return Main.progName + " unknown version; unknown build time";
        }
    }

    // From https://stackoverflow.com/questions/46454995/how-to-hide-warning-illegal-reflective-access-in-java-9-without-jvm-argument
    public static void disableIllegalAccessWarning() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Unsafe u = (Unsafe) theUnsafe.get(null);

            Class cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
            Field logger = cls.getDeclaredField("logger");
            u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
        } catch (Exception e) {
            // ignore
        }
    }
}
