package rf.ebanina.ebanina.Player.AudioPlugins;

import java.nio.file.Path;
import java.util.Map;

public interface IPluginWrapper<Plugin> {
    void processReplacing(float[][] vstInput, float[][] vstOutput, int framesRead);

    String getParameterName(int i);

    int numParameters();
    float getParameter(int i);
    void setParameter(int i, float newVal) ;

    void turnOff();
    void turnOn();

    void destroy();
    void reOpenEditor();
    void openEditor();

    String getProductString();
    String getVendorName();
    String getPluginPath();

    int numInputs();
    int numOutputs();

    String getSdkVersion();

    void save(Path path, Map<String, String> propsMap);
    boolean load(Path path, Map<String, String> out);

    String getStateExtension();
    String[] getPluginExtension();

    Plugin getPlugin();
    void setPlugin(Plugin plugin);

    @Override
    String toString();
    @Override
    boolean equals(Object o);
    @Override
    int hashCode();
}