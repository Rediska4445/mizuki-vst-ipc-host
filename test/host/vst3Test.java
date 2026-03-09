package host;

import rf.ebanina.Player.AudioPlugins.IPluginWrapper;
import rf.ebanina.Player.AudioPlugins.VST.VST3;
import rf.ebanina.Player.AudioPlugins.VST.VST3LoadException;

import java.io.File;
import java.util.concurrent.ExecutionException;

public class vst3Test {
    public static void main(String[] args) throws Exception {
        IPluginWrapper<rf.vst3> vst3PluginImpl = new VST3();

        try {
            if (vst3PluginImpl.getPlugin().asyncInit(
                    new File("C:\\Program Files\\Common Files\\VST3\\iZotope\\Ozone 9 Equalizer.vst3"),
                    44100,
                    512,
                    0,
                    true
            ).get()) {
                vst3PluginImpl.turnOn();
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new VST3LoadException();
        }

        vst3PluginImpl.openEditor();

        while (true);
    }
}
