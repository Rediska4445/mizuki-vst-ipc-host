package rf.ebanina.ebanina.Player.AudioPlugins.VST;

import rf.ebanina.ebanina.Player.AudioPlugins.IPluginWrapper;
import rf.ebanina.ebanina.Player.AudioPlugins.PluginWrapper;
import rf.vst3;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class VST3
        implements IPluginWrapper<vst3>
{
    private vst3 vst3Plugin;
    private final AtomicReference<Float> mix = new AtomicReference<>(100f);
    private final AtomicBoolean isEnable = new AtomicBoolean(false);

    private int sampleRate;
    private int maxBlockSize;
    private boolean isDoubleProcessing;
    private boolean isRealTimeProcessing;

    public static final String BIN_LIBRARIES_VST3 = File.separator + "editorhost.dll";

    public VST3() {
        this(44100, 512, false, true);
    }

    public VST3(int sampleRate, int maxBlockSize, boolean isDoubleProcessing, boolean isRealTimeProcessing) {
        this(new File(BIN_LIBRARIES_VST3), sampleRate, maxBlockSize, isDoubleProcessing, isRealTimeProcessing);
    }

    public VST3(File file, int sampleRate, int maxBlockSize, boolean isDoubleProcessing, boolean isRealTimeProcessing) {
        vst3Plugin = new vst3(file);
        this.sampleRate = sampleRate;
        this.maxBlockSize = maxBlockSize;
        this.isDoubleProcessing = isDoubleProcessing;
        this.isRealTimeProcessing = isRealTimeProcessing;
        vst3Plugin.setLoggingEnable(false);
    }

    @Override
    public void processReplacing(float[][] vstInput, float[][] vstOutput, int framesRead) {
        if(isEnable.get()) {
            int vstInBuses = vst3Plugin.getNumInputs();
            int vstOutBuses = vst3Plugin.getNumOutputs();

            int totalInChannels = 0;
            for (int i = 0; i < vstInBuses; i++) {
                totalInChannels += vst3Plugin.getNumChannelsForInputBus(i);
            }

            int totalOutChannels = 0;
            for (int i = 0; i < vstOutBuses; i++) {
                totalOutChannels += vst3Plugin.getNumChannelsForOutputBus(i);
            }

            float[][] tmpIn = new float[totalInChannels][framesRead];
            float[][] tmpOut = new float[totalOutChannels][framesRead];

            for (int ch = 0; ch < totalInChannels; ch++) {
                if (ch < vstInput.length) {
                    System.arraycopy(vstInput[ch], 0, tmpIn[ch], 0, framesRead);
                } else {
                    Arrays.fill(tmpIn[ch], 0, framesRead, 0f);
                }
            }

            vst3Plugin.process(tmpIn, tmpOut, framesRead);

            for (int ch = 0; ch < totalOutChannels; ch++) {
                if (ch < vstOutput.length) {
                    System.arraycopy(tmpOut[ch], 0, vstOutput[ch], 0, framesRead);
                }
            }
        } else {
            System.arraycopy(vstInput, 0, vstOutput, 0, vstInput.length);
        }
    }

    public vst3 getVst3Plugin() {
        return vst3Plugin;
    }

    public VST3 setVst3Plugin(vst3 vst3Plugin) {
        this.vst3Plugin = vst3Plugin;
        return this;
    }

    public AtomicReference<Float> getMix() {
        return mix;
    }

    public AtomicBoolean getIsEnable() {
        return isEnable;
    }

    public int getSampleRate() {
        return sampleRate;
    }

    public VST3 setSampleRate(int sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    public int getMaxBlockSize() {
        return maxBlockSize;
    }

    public VST3 setMaxBlockSize(int maxBlockSize) {
        this.maxBlockSize = maxBlockSize;
        return this;
    }

    public boolean isDoubleProcessing() {
        return isDoubleProcessing;
    }

    public VST3 setDoubleProcessing(boolean doubleProcessing) {
        isDoubleProcessing = doubleProcessing;
        return this;
    }

    public boolean isRealTimeProcessing() {
        return isRealTimeProcessing;
    }

    public VST3 setRealTimeProcessing(boolean realTimeProcessing) {
        isRealTimeProcessing = realTimeProcessing;
        return this;
    }

    @Override
    public String getParameterName(int i) {
        return null;
    }

    @Override
    public int numParameters() {
        return 0;
    }

    @Override
    public float getParameter(int i) {
        return 0;
    }

    @Override
    public void setParameter(int i, float newVal) {
        // TODO
    }

    @Override
    public void turnOff() {
        isEnable.set(false);
    }

    @Override
    public void turnOn() {
        isEnable.set(true);
    }

    @Override
    public void destroy() {
        if(isEnable.get()) {
            vst3Plugin.executor.submit(() -> vst3Plugin.destroy());
        }
    }

    @Override
    public void reOpenEditor() {
        if(isEnable.get()) {
            vst3Plugin.asyncReCreateView();
        }
    }

    @Override
    public void openEditor() {
        if(isEnable.get()) {
            vst3Plugin.asyncCreateView();
        }
    }

    @Override
    public String getProductString() {
        return vst3Plugin.getPluginName();
    }

    @Override
    public String getVendorName() {
        return vst3Plugin.getVendor();
    }

    @Override
    public String getPluginPath() {
        return vst3Plugin.plugin.getAbsolutePath();
    }

    @Override
    public int numInputs() {
        return vst3Plugin.getNumInputs() <= 1 ? 2 : vst3Plugin.getNumInputs();
    }

    @Override
    public int numOutputs() {
        return vst3Plugin.getNumOutputs() <= 1 ? 2 : vst3Plugin.getNumOutputs();
    }

    @Override
    public String getSdkVersion() {
        return vst3Plugin.getSdkVersion();
    }

    @Override
    public void save(Path path, Map<String, String> propsMap) {
        propsMap.put("type", PluginWrapper.Type.VST3.name());
        propsMap.put("pluginPath", getPluginPath());
        propsMap.put("productString", getProductString());
        propsMap.put("vendorName", getVendorName());
        propsMap.put("isEnable", String.valueOf(isEnable.get()));
        propsMap.put("numInputs", String.valueOf(numInputs()));
        propsMap.put("numOutputs", String.valueOf(numOutputs()));

        try {
            propsMap.put("numParameters", String.valueOf(vst3Plugin.getParameterCount()));

            for (int i = 0; i < vst3Plugin.getParameterCount(); i++) {
                propsMap.put("param." + i + ".value", String.valueOf(vst3Plugin.getParameterValue(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean load(Path path, Map<String, String> out) {
        vst3Plugin = new vst3(new File(BIN_LIBRARIES_VST3));
        vst3Plugin.setLoggingEnable(false);

        vst3Plugin.setOnInitialize(() -> {
            int numParams = Integer.parseInt(out.get("numParameters"));

            for (int i = 0; i < numParams && i < vst3Plugin.getParameterCount(); i++) {
                String valueStr = out.get("param." + i + ".value");

                if (valueStr != null) {
                    vst3Plugin.setParameterValue(i, Float.parseFloat(valueStr));
                }
            }
        });

        try {
            if(!vst3Plugin.asyncInit(
                    new File(out.get("pluginPath")),
                    sampleRate,
                    maxBlockSize,
                    isDoubleProcessing ? 1 : 0,
                    isRealTimeProcessing
            ).get()) {
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();

            return false;
        }

        return true;
    }

    @Override
    public String getStateExtension() {
        return PluginWrapper.Type.VST.stateExtension;
    }

    @Override
    public String[] getPluginExtension() {
        return PluginWrapper.Type.VST3.fileExtension;
    }

    @Override
    public vst3 getPlugin() {
        return vst3Plugin;
    }

    @Override
    public void setPlugin(vst3 vst3) {
        this.vst3Plugin = vst3;
    }

    @Override
    public String toString() {
        return "VST3{" +
                "vst3Plugin=" + vst3Plugin +
                ", mix=" + mix +
                ", isEnable=" + isEnable +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VST3 vst3 = (VST3) o;
        return Objects.equals(vst3Plugin, vst3.vst3Plugin) && Objects.equals(mix, vst3.mix) && Objects.equals(isEnable, vst3.isEnable);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vst3Plugin.plugin.getAbsolutePath());
    }
}