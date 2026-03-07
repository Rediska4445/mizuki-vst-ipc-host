package rf.ebanina.Player.AudioPlugins;

import com.synthbot.audioplugin.vst.JVstLoadException;
import com.synthbot.audioplugin.vst.vst2.JVstHost2;
import javafx.beans.property.SimpleObjectProperty;
import rf.ebanina.Player.AudioPlugins.VST.VST;
import rf.ebanina.Player.AudioPlugins.VST.VST3;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PluginWrapper {
    private IPluginWrapper<?> plugin;

    public enum Type {
        VST("steinberg", "dll"),
        VST3("steinberg","vst3");

        public final String stateExtension;
        public final String[] fileExtension;

        Type(String stateExtension, String... fileExtension) {
            this.stateExtension = stateExtension;
            this.fileExtension = fileExtension;
        }
    }

    private final AtomicBoolean isEnable = new AtomicBoolean(true);

    private final SimpleObjectProperty<AtomicInteger> mix = new SimpleObjectProperty<>(new AtomicInteger(100));

    public void setEnable(boolean isEnable) {
        this.isEnable.set(isEnable);
    }

    public boolean isEnable() {
        return isEnable.get();
    }

    public PluginWrapper(IPluginWrapper<?> plugin) {
        this.plugin = plugin;
    }

    public IPluginWrapper<?> getPlugin() {
        return plugin;
    }

    public PluginWrapper setPlugin(IPluginWrapper<?> plugin) {
        this.plugin = plugin;
        return this;
    }

    public PluginWrapper() {}

    public String getParameterName(int i) {
        return plugin.getParameterName(i);
    }

    public Runnable onInit = null;

    public float getMix() {
        return mix.get().get();
    }

    public PluginWrapper setMix(int mix) {
        this.mix.get().set(mix);
        return this;
    }

    public void processReplacing(float[][] vstInput, float[][] vstOutput, int framesRead) {
        if(isEnable.get()) {
            plugin.processReplacing(vstInput, vstOutput, framesRead);

            float mixValue = getMix() / 100.0f;

            if (mixValue < 1.0f) {
                for (int channel = 0; channel < vstOutput.length; channel++) {
                    for (int i = 0; i < framesRead; i++) {
                        vstOutput[channel][i] = (1.0f - mixValue) * vstInput[channel][i] +
                                mixValue * vstOutput[channel][i];
                    }
                }
            }
        } else {
            System.arraycopy(vstInput, 0, vstOutput, 0, vstInput.length);
        }
    }

    public float getParameter(int i) {
        return plugin.getParameter(i);
    }

    public int numParameters() {
        return plugin.numParameters();
    }

    public void setParameter(int i, float newVal) {
        plugin.setParameter(i, newVal);
    }

    public void turnOff() {
        isEnable.set(false);

        plugin.turnOff();
    }

    public void turnOn() {
        isEnable.set(true);

        plugin.turnOn();

        if(onInit != null) {
            onInit.run();
        }
    }

    public void destroy() {
        if(isEnable.get()) {
            plugin.destroy();
        }
    }

    public void reOpenVst3GUI() {
        if(isEnable.get()) {
            plugin.reOpenEditor();
        }
    }

    public void openEditor() {
        if(isEnable.get()) {
            plugin.openEditor();
        }
    }

    public String getProductString() {
        return plugin.getProductString();
    }

    public String getVendorName() {
        return plugin.getVendorName();
    }

    public String getPluginPath() {
        return plugin.getPluginPath();
    }

    public int numInputs() {
        return plugin.numInputs();
    }

    public int numOutputs() {
        return plugin.numOutputs();
    }

    public String getSdkVersion() {
        return plugin.getSdkVersion();
    }

    public void saveState(Path path,
                          Runnable saveAction /* FileManager.instance.saveArray(path.toFile().getPath(), "plugin", propsMap); */
    ) {
        Map<String, String> propsMap = new HashMap<>();

        propsMap.put("pluginPath", getPluginPath());
        propsMap.put("isEnable", String.valueOf(isEnable.get()));
        propsMap.put("mix", String.valueOf(getMix()));

        plugin.save(path, propsMap);

        saveAction.run();
    }

    public boolean loadState(Path path,
                             Supplier<Map<String, String>> outAction /* FileManager.instance.readArray(path.toFile().getPath(), "plugin", Map.of()) */
    ) {
        Map<String, String> out = outAction.get();

        try {
            this.setEnable(Boolean.parseBoolean(out.get("isEnable")));
            this.setMix((int) Float.parseFloat(out.get("mix")));

            String type = out.get("type");

            if(type == null)
                return false;

            if(type.equalsIgnoreCase(Type.VST.name())) {
                try {
                    plugin = new VST(JVstHost2.newInstance(new File(out.get("pluginPath"))));
                } catch (FileNotFoundException | JVstLoadException e) {
                    e.printStackTrace();

                    return false;
                }
            } else if(type.equalsIgnoreCase(Type.VST3.name())) {
                plugin = new VST3();
            }

            return plugin.load(path, out);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getStateExtension() {
        return plugin.getStateExtension();
    }

    public String[] getPluginExtension() {
        return plugin.getPluginExtension();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PluginWrapper plugin1 = (PluginWrapper) o;
        return Objects.equals(plugin, plugin1.plugin);
    }

    @Override
    public int hashCode() {
        return plugin.hashCode();
    }

    @Override
    public String toString() {
        return plugin.toString();
    }
}