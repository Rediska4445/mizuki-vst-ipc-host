package rf.ebanina.Player.AudioPlugins.VST;

import com.synthbot.audioplugin.vst.vst2.JVstHost2;
import rf.ebanina.Player.AudioPlugins.IPluginWrapper;
import rf.ebanina.Player.AudioPlugins.PluginWrapper;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public final class VST
        implements IPluginWrapper<JVstHost2>
{
    private JVstHost2 vst2Plugin;

    public VST(JVstHost2 vst2Plugin) {
        this.vst2Plugin = vst2Plugin;
    }

    @Override
    public void processReplacing(float[][] vstInput, float[][] vstOutput, int framesRead) {
        vst2Plugin.processReplacing(vstInput, vstOutput, framesRead);
    }

    @Override
    public String getParameterName(int i) {
        return vst2Plugin.getParameterName(i);
    }

    @Override
    public int numParameters() {
        return vst2Plugin.numParameters();
    }

    @Override
    public float getParameter(int i) {
        return vst2Plugin.getParameter(i);
    }

    @Override
    public void setParameter(int i, float newVal) {
        vst2Plugin.setParameter(i, newVal);
    }

    @Override
    public void turnOff() {
        vst2Plugin.turnOff();
    }

    @Override
    public void turnOn() {
        vst2Plugin.turnOn();
    }

    @Override
    public void destroy() {
        vst2Plugin.turnOffAndUnloadPlugin();
    }

    @Override
    public void reOpenEditor() {
        vst2Plugin.openEditor(getVendorName());
    }

    @Override
    public void openEditor() {
        vst2Plugin.openEditor(getVendorName());
    }

    @Override
    public String getProductString() {
        return vst2Plugin.getProductString();
    }

    @Override
    public String getVendorName() {
        return vst2Plugin.getVendorName();
    }

    @Override
    public String getPluginPath() {
        return vst2Plugin.getPluginPath();
    }

    @Override
    public int numInputs() {
        return vst2Plugin.numInputs();
    }

    @Override
    public int numOutputs() {
        return vst2Plugin.numOutputs();
    }

    @Override
    public String getSdkVersion() {
        return vst2Plugin.getVstVersion().name();
    }

    @Override
    public void save(Path path,  Map<String, String> propsMap) {
        try {
            propsMap.put("type", PluginWrapper.Type.VST.name());
            propsMap.put("pluginPath", getPluginPath());
            propsMap.put("productString", getProductString());
            propsMap.put("vendorName", getVendorName());
            propsMap.put("numInputs", String.valueOf(numInputs()));
            propsMap.put("numOutputs", String.valueOf(numOutputs()));

            propsMap.put("numParameters", String.valueOf(vst2Plugin.numParameters()));

            for (int i = 0; i < vst2Plugin.numParameters(); i++) {
                propsMap.put("param." + i + ".name", vst2Plugin.getParameterName(i));
                propsMap.put("param." + i + ".value", String.valueOf(vst2Plugin.getParameter(i)));
                propsMap.put("param." + i + ".label", vst2Plugin.getParameterLabel(i));
                propsMap.put("param." + i + ".display", vst2Plugin.getParameterDisplay(i));
            }

            propsMap.put("getBankChunk", Arrays.toString(vst2Plugin.getBankChunk()));
            propsMap.put("getEffectName", String.valueOf(vst2Plugin.getEffectName()));
            propsMap.put("getVstVersion", String.valueOf(vst2Plugin.getVstVersion()));
            propsMap.put("getProgram", String.valueOf(vst2Plugin.getProgram()));
            propsMap.put("canProcessReplacing", String.valueOf(vst2Plugin.canReplacing()));
            propsMap.put("getSampleRate", String.valueOf(vst2Plugin.getSampleRate()));
            propsMap.put("getBlockSize", String.valueOf(vst2Plugin.getBlockSize()));
            propsMap.put("getInputProperties", String.valueOf(vst2Plugin.getInputProperties(0)));
            propsMap.put("getOutputProperties", String.valueOf(vst2Plugin.getOutputProperties(0)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean load(Path path, Map<String, String> out) {
        int numParams = Integer.parseInt(out.get("numParameters"));

        for (int i = 0; i < numParams && i < vst2Plugin.numParameters(); i++) {
            String valueStr = out.get("param." + i + ".value");

            if (valueStr != null) {
                float value = Float.parseFloat(valueStr);
                vst2Plugin.setParameter(i, value);
            }
        }

        return true;
    }

    @Override
    public String getStateExtension() {
        return PluginWrapper.Type.VST.stateExtension;
    }

    @Override
    public String[] getPluginExtension() {
        return PluginWrapper.Type.VST.fileExtension;
    }

    @Override
    public JVstHost2 getPlugin() {
        return vst2Plugin;
    }

    @Override
    public void setPlugin(JVstHost2 jVstHost2) {
        this.vst2Plugin = jVstHost2;
    }

    @Override
    public String toString() {
        return "VST{" +
                "vst2Plugin=" + vst2Plugin +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VST vst = (VST) o;
        return Objects.equals(vst2Plugin.getPluginPath(), vst.vst2Plugin.getPluginPath());
    }

    @Override
    public int hashCode() {
        return Objects.hash(vst2Plugin.getPluginPath());
    }
}