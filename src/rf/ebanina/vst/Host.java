package rf.ebanina.vst;

import com.synthbot.audioplugin.vst.vst2.JVstHost2;

import com.synthbot.audioplugin.vst.vst2.JVstHost24;
import rf.ebanina.Player.AudioPlugins.IPluginWrapper;
import rf.ebanina.Player.AudioPlugins.PluginWrapper;
import rf.ebanina.Player.AudioPlugins.VST.VST;
import rf.ebanina.Player.AudioPlugins.VST.VST3;
import rf.ebanina.Player.AudioPlugins.VST.VST3LoadException;
import rf.ebanina.utils.loggining.Log;

import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.io.*;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class Host
{
    public PluginWrapper pluginWrapper;

    static int DEFAULT_PORT = 888;
    static String DEFAULT_HOST = "localhost";
    static int MAX_CHANNELS = 8;
    static int MAX_SAMPLES = 4096;
    static int MAX_DATA_SIZE = MAX_CHANNELS * MAX_SAMPLES;

    public static final Log logger = new Log();

    private static final Host mainHost = new Host();

    public static void main(String[] args) throws Exception {
        // === ARGUMENT PARSING ===
        int port = DEFAULT_PORT;
        String host = DEFAULT_HOST;
        int maxChannels = MAX_CHANNELS;
        int maxSamples = MAX_SAMPLES;
        boolean verbose = true;
        String defaultProcessMode = "plugin";

        logger.init_file_log("logs/ipc-out.log", "logs/ipc-err.log");

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-port": case "-p":
                    if (i + 1 < args.length) port = Integer.parseInt(args[++i]);
                    else { printUsage(); return; }
                    break;
                case "-host": case "-h":
                    if (i + 1 < args.length) host = args[++i];
                    else { printUsage(); return; }
                    break;
                case "-max-channels":
                    if (i + 1 < args.length) maxChannels = Integer.parseInt(args[++i]);
                    else { printUsage(); return; }
                    break;
                case "-max-samples":
                    if (i + 1 < args.length) maxSamples = Integer.parseInt(args[++i]);
                    else { printUsage(); return; }
                    break;
                case "-mode": case "-m":
                    if (i + 1 < args.length) defaultProcessMode = args[++i];
                    else { printUsage(); return; }
                    break;
                case "-verbose": case "-v":
                    verbose = true;
                    break;
                case "-quiet": case "-q":
                    verbose = false;
                    break;
                case "-help": case "--help":
                    printUsage();
                    return;
                default:
                    if (args[i].startsWith("-")) {
                        logger.severe("Unknown argument: " + args[i]);
                        printUsage();
                        return;
                    }
            }
        }

        MAX_CHANNELS = maxChannels;
        MAX_SAMPLES = maxSamples;
        MAX_DATA_SIZE = MAX_CHANNELS * MAX_SAMPLES;

        if (verbose) {
            logger.info("=== VST IPC Host ===");
            logger.info("Listening: " + host + ":" + port);
            logger.info("Max channels: " + maxChannels + ", Max samples: " + maxSamples);
            logger.info("====================");
        }

        // === START SERVER ===
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().setReuseAddress(true);
        server.bind(new InetSocketAddress(host, port));

        logger.info("Host started on " + host + ":" + port);

        ByteBuffer cmdTextBuf = ByteBuffer.allocate(65536);
        ByteBuffer dataBuf = ByteBuffer.allocate(MAX_DATA_SIZE * 4);

        String processMode = defaultProcessMode;

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down...");
            if (mainHost.pluginWrapper != null) {
                mainHost.pluginWrapper.turnOff();
                mainHost.pluginWrapper.destroy();
                mainHost.pluginWrapper = null;
            }
        }));

        // === MAIN LOOP ===
        while (true) {
            SocketChannel client = null;

            try {
                client = server.accept();
                logger.info("+ Client connected from " + client.getRemoteAddress());

                CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT);
                CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();

                int msgCounter = 0;

                while (client.isOpen()) {
                    msgCounter++;

                    // === ЧТЕНИЕ ДЛИНЫ ===
                    ByteBuffer lenBuf = ByteBuffer.allocate(4);
                    int readBytes = readFully(client, lenBuf, 4);
                    if (readBytes < 0) {
                        logger.info("[" + msgCounter + "] Client disconnected while reading length");
                        break;
                    }

                    lenBuf.flip();
                    int messageLength = lenBuf.getInt();

                    logger.info("[" + msgCounter + "] Raw length bytes: " +
                            String.format("%02X %02X %02X %02X",
                                    lenBuf.get(0), lenBuf.get(1), lenBuf.get(2), lenBuf.get(3)) +
                            ", messageLength=" + messageLength);

                    // Защита: невозможная длина
                    if (messageLength <= 0 || messageLength > cmdTextBuf.capacity()) {
                        logger.warn("[" + msgCounter + "] Invalid message length: " + messageLength +
                                ", capacity=" + cmdTextBuf.capacity() +
                                " -> closing client socket");

                        client.close();
                        break;
                    }

                    // === ЧТЕНИЕ ТЕКСТА ===
                    cmdTextBuf.clear();
                    cmdTextBuf.limit(messageLength);
                    readBytes = readFully(client, cmdTextBuf, messageLength);
                    if (readBytes < 0) {
                        logger.info("[" + msgCounter + "] Client disconnected during command read");
                        break;
                    }
                    if (readBytes != messageLength) {
                        logger.warn("[" + msgCounter + "] readFully for command: expected=" +
                                messageLength + ", got=" + readBytes);
                    }

                    cmdTextBuf.flip();

                    // Декодирование текста
                    String commandString;
                    try {
                        CharBuffer charBuf = decoder.decode(cmdTextBuf);
                        commandString = charBuf.toString().trim();
                    } catch (java.nio.charset.MalformedInputException e) {
                        logger.warn("[" + msgCounter + "] Malformed UTF-8, len=" + messageLength +
                                ", error=" + e.getMessage());
                        continue;
                    }

                    logger.info("[" + msgCounter + "] Received commandString = '" + commandString + "'");

                    // Парсинг команды
                    String[] parts = commandString.split(";");
                    if (parts.length == 0) {
                        logger.warn("[" + msgCounter + "] Empty command string");
                        sendTextError(client, encoder, "Empty command");
                        continue;
                    }

                    String command = parts[0].trim().toUpperCase();
                    String[] argsList = new String[parts.length - 1];
                    for (int i = 1; i < parts.length; i++) {
                        argsList[i - 1] = parts[i].trim();
                    }

                    logger.info("[" + msgCounter + "] Command = " + command +
                            ", argsCount=" + argsList.length);

                    // Обработка
                    String response = mainHost.handleTextCommand(
                            command,
                            argsList,
                            processMode,
                            verbose,
                            client,
                            dataBuf,
                            encoder
                    );

                    if (!response.isEmpty()) {
                        logger.info("[" + msgCounter + "] Sending response: " + response);
                        sendTextResponse(client, encoder, response);
                    } else {
                        logger.info("[" + msgCounter + "] Response already sent by handler");
                    }
                }

                if (client != null && client.isOpen()) {
                    client.close();
                    logger.info("Client socket closed");
                }

            } catch (Exception e) {
                logger.warn("Connection error: " + e.getMessage() + "\n\tStackTrace: \n");
                e.printStackTrace();

                if (client != null && client.isOpen()) {
                    try {
                        client.close();
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    private String handleTextCommand(
            String command,
            String[] args,
            String processMode,
            boolean verbose,
            SocketChannel client,
            ByteBuffer dataBuf,
            CharsetEncoder encoder) throws Exception {

        switch (command) {
            case "INIT_PLUGIN": {
                if (args.length < 5) {
                    return "ERROR:INIT_PLUGIN requires at least 5 arguments: type, path, sampleRate, blockSize, isRealtime";
                }

                if (pluginWrapper != null) {
                    logger.info("Terminating existing plugin...");
                    pluginWrapper.turnOff();
                    pluginWrapper.destroy();
                    pluginWrapper = null;
                }

                String type = args[0];
                String path = args[1];
                int sampleRate = Integer.parseInt(args[2]);
                int blockSize = Integer.parseInt(args[3]);
                boolean isRealtime = Boolean.parseBoolean(args[4]);
                boolean isDoubleBuffering = args.length > 5 && Boolean.parseBoolean(args[5]);

                logger.info("INIT_PLUGIN: type=" + type + ", path=" + path +
                        ", sr=" + sampleRate + ", bs=" + blockSize +
                        ", realtime=" + isRealtime + ", double=" + isDoubleBuffering);

                pluginWrapper = loadPlugin(type, path, sampleRate, blockSize, isRealtime, isDoubleBuffering);

                if (pluginWrapper == null) {
                    return "ERROR:Failed to load plugin: " + path;
                }

                logger.info("+ Plugin loaded: " + pluginWrapper.getProductString());
                logger.info("  Vendor: " + pluginWrapper.getVendorName());
                logger.info("  I/O: " + pluginWrapper.numInputs() + "/" + pluginWrapper.numOutputs());
                logger.info("  Parameters: " + pluginWrapper.numParameters());

                return "OK:" + pluginWrapper.getVendorName() + ";" +
                        pluginWrapper.numParameters() + ";" +
                        pluginWrapper.numInputs() + ";" +
                        pluginWrapper.numOutputs();
            }

            case "TERMINATE_PLUGIN": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                pluginWrapper.turnOff();
                pluginWrapper.destroy();
                pluginWrapper = null;
                logger.info("Plugin terminated");
                return "OK:Plugin terminated";
            }

            case "SET_PARAMETER": {
                if (args.length != 2) {
                    return "ERROR:SET_PARAMETER requires 2 arguments: index, value";
                }
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }

                int index = Integer.parseInt(args[0]);
                float value = Float.parseFloat(args[1]);

                if (index < 0 || index >= pluginWrapper.numParameters()) {
                    return "ERROR:Invalid parameter index: " + index;
                }

                pluginWrapper.setParameter(index, value);

                if (verbose) {
                    logger.info("Parameter " + index + " (" + pluginWrapper.getParameterName(index) + ") = " + value);
                }

                return "OK:Parameter " + index + " set to " + value;
            }

            case "GET_PARAMETER": {
                if (args.length != 1) {
                    return "ERROR:GET_PARAMETER requires 1 argument: index";
                }
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }

                int index = Integer.parseInt(args[0]);
                if (index < 0 || index >= pluginWrapper.numParameters()) {
                    return "ERROR:Invalid parameter index: " + index;
                }

                float value = pluginWrapper.getParameter(index);
                return "OK:" + value;
            }

            case "GET_PARAM_COUNT": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                int count = pluginWrapper.numParameters();
                return "OK:" + count;
            }

            case "GET_PARAM_INFO": {
                if (args.length != 1) {
                    return "ERROR:GET_PARAM_INFO requires 1 argument: index";
                }
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }

                int index = Integer.parseInt(args[0]);
                if (index < 0 || index >= pluginWrapper.numParameters()) {
                    return "ERROR:Invalid parameter index: " + index;
                }

                String paramName = pluginWrapper.getParameterName(index);
                return "OK:" + paramName;
            }

            case "OPEN_EDITOR": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                try {
                    pluginWrapper.openEditor();

                    return "OK:Editor opened";
                } catch (Exception e) {
                    return "ERROR:Failed to open editor: " + e.getMessage();
                }
            }

            case "CLOSE_EDITOR": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                try {
                    pluginWrapper.getPlugin().reOpenEditor();
                    return "OK:Editor closed";
                } catch (Exception e) {
                    return "ERROR:Failed to close editor: " + e.getMessage();
                }
            }

            case "SAVE_STATE": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                try {
                    Path tempFile = java.nio.file.Files.createTempFile("plugin_state", "." + pluginWrapper.getStateExtension());
                    Map<String, String> propsMap = new java.util.HashMap<>();
                    pluginWrapper.getPlugin().save(tempFile, propsMap);

                    byte[] stateData = java.nio.file.Files.readAllBytes(tempFile);
                    java.nio.file.Files.deleteIfExists(tempFile);

                    String base64Data = java.util.Base64.getEncoder().encodeToString(stateData);
                    return "OK:" + base64Data;
                } catch (Exception e) {
                    return "ERROR:Failed to save state: " + e.getMessage();
                }
            }

            case "LOAD_STATE": {
                if (args.length != 1) {
                    return "ERROR:LOAD_STATE requires 1 argument: base64 encoded state";
                }
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }

                try {
                    byte[] stateData = java.util.Base64.getDecoder().decode(args[0]);
                    Path tempFile = java.nio.file.Files.createTempFile("plugin_state", "." + pluginWrapper.getStateExtension());
                    java.nio.file.Files.write(tempFile, stateData);

                    Map<String, String> outProps = new java.util.HashMap<>();
                    boolean success = pluginWrapper.getPlugin().load(tempFile, outProps);

                    java.nio.file.Files.deleteIfExists(tempFile);

                    if (success) {
                        return "OK:State loaded";
                    } else {
                        return "ERROR:Failed to load state";
                    }
                } catch (Exception e) {
                    return "ERROR:Failed to load state: " + e.getMessage();
                }
            }

            case "PROCESS_AUDIO": {
                if (args.length < 3) {
                    return "ERROR:PROCESS_AUDIO requires 3 arguments: channels, samples, framesRead";
                }

                int channels   = Integer.parseInt(args[0]);
                int samples    = Integer.parseInt(args[1]);
                int framesRead = Integer.parseInt(args[2]);

                // Проверяем каналы (от них зависит, сколько вообще байт нам пришло)
                if (channels <= 0 || channels > MAX_CHANNELS) {
                    return "ERROR:Invalid channels: " + channels;
                }

                int expectedBytes = channels * samples * 4;

                // Если client уже отправил binaryData, мы ДОЛЖНЫ эти байты вычитать,
                // даже если samples некорректны, иначе поток съедет.
                if (samples <= 0 || samples > MAX_SAMPLES) {
                    if (expectedBytes > 0 && expectedBytes <= dataBuf.capacity()) {
                        dataBuf.clear();
                        dataBuf.order(ByteOrder.LITTLE_ENDIAN);
                        dataBuf.limit(expectedBytes);

                        int skipped = readFully(client, dataBuf, expectedBytes);
                        logger.warn("PROCESS_AUDIO: skipping " + skipped +
                                " bytes due to invalid samples=" + samples +
                                ", expectedBytes=" + expectedBytes);
                    } else {
                        logger.warn("PROCESS_AUDIO: invalid samples=" + samples +
                                ", cannot skip binaryData safely (expectedBytes=" +
                                expectedBytes + ", capacity=" + dataBuf.capacity() + ")");
                    }

                    return "ERROR:Invalid samples: " + samples;
                }

                // Проверка на переполнение буфера
                if (expectedBytes > dataBuf.capacity()) {
                    // Здесь клиент уже отправил binaryData, но мы не можем его положить в dataBuf.
                    // Всё равно читаем и выкидываем, чтобы не сломать поток.
                    dataBuf.clear();
                    dataBuf.limit(dataBuf.capacity());
                    int toSkip = dataBuf.capacity();
                    int skipped = 0;
                    while (skipped < expectedBytes) {
                        int chunk = Math.min(toSkip, expectedBytes - skipped);
                        dataBuf.clear();
                        dataBuf.limit(chunk);
                        int r = readFully(client, dataBuf, chunk);
                        if (r < 0) break;
                        skipped += r;
                    }
                    logger.warn("PROCESS_AUDIO: buffer overflow, requested " + expectedBytes +
                            " bytes, max " + dataBuf.capacity() + ", skipped=" + skipped);
                    return "ERROR:Buffer overflow: requested " + expectedBytes +
                            " bytes, max " + dataBuf.capacity();
                }

                // --- НОРМАЛЬНОЕ ЧТЕНИЕ АУДИО ДАННЫХ ---
                dataBuf.clear();
                dataBuf.order(ByteOrder.LITTLE_ENDIAN);
                dataBuf.limit(expectedBytes);

                int readBytes = readFully(client, dataBuf, expectedBytes);
                if (readBytes < 0) {
                    return "ERROR:Failed to read audio data";
                }
                if (readBytes != expectedBytes) {
                    logger.warn("PROCESS_AUDIO: readFully audio: expected " +
                            expectedBytes + ", got " + readBytes);
                }
                dataBuf.flip();

                FloatBuffer fb = dataBuf.asFloatBuffer();

                float[][] inputs  = new float[channels][samples];
                float[][] outputs = new float[channels][samples];

                for (int s = 0; s < samples; s++) {
                    for (int ch = 0; ch < channels; ch++) {
                        if (fb.hasRemaining()) {
                            inputs[ch][s] = fb.get();
                        }
                    }
                }

                if (processMode.equals("plugin") && pluginWrapper != null) {
                    pluginWrapper.processReplacing(inputs, outputs, framesRead);

                    fb.rewind();
                    for (int ch = 0; ch < channels; ch++) {
                        for (int s = 0; s < samples; s++) {
                            fb.put(outputs[ch][s]);
                        }
                    }
                }

                // Отправка ответа (текст + бинарные данные)
                sendTextResponse(client, encoder, "OK:" + channels + ";" + samples);

                dataBuf.rewind();
                dataBuf.order(ByteOrder.LITTLE_ENDIAN);
                while (dataBuf.hasRemaining()) {
                    client.write(dataBuf);
                }

                dataBuf.clear();
                return "";
            }

            case "SET_MODE": {
                if (args.length != 1) {
                    return "ERROR:SET_MODE requires 1 argument";
                }
                String newMode = args[0].toLowerCase();
                if (!newMode.equals("echo") && !newMode.equals("silence") &&
                        !newMode.equals("gain") && !newMode.equals("invert") &&
                        !newMode.equals("delay") && !newMode.equals("distort") &&
                        !newMode.equals("plugin")) {
                    return "ERROR:Invalid mode: " + newMode;
                }
                return "OK:Mode set to " + newMode;
            }

            case "GET_PLUGIN_INFO": {
                if (pluginWrapper == null) {
                    return "ERROR:No plugin loaded";
                }
                return "OK:" + pluginWrapper.getProductString() + ";" +
                        pluginWrapper.getVendorName() + ";" +
                        pluginWrapper.getSdkVersion() + ";" +
                        pluginWrapper.getPluginPath();
            }

            case "HELP": {
                return "OK:Commands: INIT_PLUGIN, TERMINATE_PLUGIN, SET_PARAMETER, GET_PARAMETER, " +
                        "GET_PARAM_COUNT, GET_PARAM_INFO, OPEN_EDITOR, CLOSE_EDITOR, " +
                        "SAVE_STATE, LOAD_STATE, PROCESS_AUDIO, SET_MODE, GET_PLUGIN_INFO, HELP";
            }

            default: {
                return "ERROR:Unknown command: " + command;
            }
        }
    }

    private static void printUsage() {
        try {
            InputStream is = Host.class.getResourceAsStream("usage.txt");
            if (is == null) {
                // Fallback if file not found
                is = Thread.currentThread().getContextClassLoader()
                        .getResourceAsStream("rf/ebanina/vst/usage.txt");
            }

            if (is != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            } else {
                // Minimal usage if file not found
                System.out.println("\n=== VST Host ===");
                System.out.println("Usage: java -cp <classpath> rf.ebanina.vst.Host [options]");
                System.out.println("Options: -port, -host, -max-channels, -max-samples, -mode, -quiet, -help");
                System.out.println("================\n");
            }
        } catch (Exception e) {
            System.err.println("Error loading usage.txt: " + e.getMessage());
        }
    }

    private static void sendTextResponse(SocketChannel client, CharsetEncoder encoder, String response) throws Exception {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        ByteBuffer lenBuf = ByteBuffer.allocate(4).putInt(responseBytes.length);
        lenBuf.flip();
        client.write(lenBuf);

        ByteBuffer dataBuf = ByteBuffer.wrap(responseBytes);
        while (dataBuf.hasRemaining()) {
            client.write(dataBuf);
        }
    }

    private static void sendTextError(SocketChannel client, CharsetEncoder encoder, String error) throws Exception {
        sendTextResponse(client, encoder, "ERROR:" + error);
    }

    public static PluginWrapper loadPlugin(String type, String path, int sampleRate, int blockSize, boolean isRealtime, boolean isDoubleBuffering) throws Exception {
        logger.info("Loading plugin: " + path + " @ " + sampleRate + "Hz, " + blockSize + " samples, " + type + "-Type");

        if(type.equalsIgnoreCase(PluginWrapper.Type.VST3.name())) {
            VST3 vst = new VST3(sampleRate, blockSize, isDoubleBuffering, isRealtime);

            try {
                if (vst.getPlugin().asyncInit(
                        new File("C:\\Program Files\\Common Files\\VST3\\iZotope\\Ozone 9 Equalizer.vst3"),
                        44100,
                        512,
                        0,
                        true
                ).get()) {
                    vst.turnOn();
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new VST3LoadException();
            }

            return new PluginWrapper().setPlugin(vst);
        } else if(type.equalsIgnoreCase(PluginWrapper.Type.VST.name())) {
            return new PluginWrapper().setPlugin(new VST(JVstHost24.newInstance(new File(path), sampleRate, blockSize)));
        }

        return null;
    }

    public static int readFully(SocketChannel channel, ByteBuffer buffer, int expectedBytes) throws Exception {
        int totalRead = 0;
        buffer.limit(expectedBytes);
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer);
            if (read == -1) return -1;
            totalRead += read;
        }
        return totalRead;
    }
}
