package rf.ebanina.vst;

import rf.ebanina.utils.loggining.Log;

import java.nio.*;
import java.nio.channels.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Host {
    static int DEFAULT_PORT = 888;
    static String DEFAULT_HOST = "localhost";
    static int MAX_CHANNELS = 8;
    static int MAX_SAMPLES = 4096;
    static int MAX_DATA_SIZE = MAX_CHANNELS * MAX_SAMPLES;

    public static final Log logger = new Log();

    public static void main(String[] args) throws Exception {
        // === ARGUMENT PARSING ===
        int port = DEFAULT_PORT;
        String host = DEFAULT_HOST;
        int maxChannels = MAX_CHANNELS;
        int maxSamples = MAX_SAMPLES;
        boolean verbose = true;
        String processMode = "echo";

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
                    if (i + 1 < args.length) {
                        processMode = args[++i];
                        if (!processMode.equals("echo") &&
                                !processMode.equals("silence") &&
                                !processMode.equals("gain") &&
                                !processMode.equals("invert") &&
                                !processMode.equals("delay") &&
                                !processMode.equals("distort")) {
                            logger.severe("Invalid mode: " + processMode + ". Use: echo, silence, gain, invert, delay, distort");
                            printUsage();
                            return;
                        }
                    } else { printUsage(); return; }
                    break;
                case "-quiet": case "-q":
                    verbose = false;
                    break;
                case "-verbose": case "-v":
                    verbose = true;
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
            logger.info("=== VST Host Configuration ===");
            logger.info("Host: " + host);
            logger.info("Port: " + port);
            logger.info("Max Channels: " + maxChannels);
            logger.info("Max Samples: " + maxSamples);
            logger.info("Max Data Size: " + MAX_DATA_SIZE + " floats");
            logger.info("Process Mode: " + processMode);
            logger.info("Verbose: " + verbose);
            logger.info("=============================");
        }

        // === START SERVER ===
        ServerSocketChannel server = ServerSocketChannel.open();
        server.socket().setReuseAddress(true);

        try {
            server.bind(new InetSocketAddress(host, port));
        } catch (BindException e) {
            logger.severe("Failed to bind to port " + port + ": " + e.getMessage());
            System.exit(2);
            return;
        }

        logger.info("VST Host started on " + host + ":" + port);
        logger.info("Waiting for connections... (Ctrl+C to stop)");

        ByteBuffer hdr = ByteBuffer.allocate(20);
        ByteBuffer respHdr = ByteBuffer.allocate(8);
        ByteBuffer dataBuf = ByteBuffer.allocate(MAX_DATA_SIZE * 4);

        // === INFINITE LOOP ===
        while (true) {
            SocketChannel client = null;
            try {
                if (verbose) logger.info("\n=== WAITING FOR CLIENT ===");
                client = server.accept();
                logger.info("✓ Client connected");

                while (true) {
                    if (verbose) logger.info("=== NEW REQUEST ===");

                    hdr.clear();
                    int headerBytes = readFully(client, hdr, 20);

                    if (headerBytes < 0) {
                        logger.info("Client disconnected. Waiting for next connection...");
                        break;
                    }

                    hdr.flip();
                    int channels = hdr.getInt();
                    int samples = hdr.getInt();
                    int inputSize = hdr.getInt();
                    int outputSize = hdr.getInt();
                    int framesRead = hdr.getInt();

                    if (verbose) {
                        logger.info("Request: channels=" + channels + ", samples=" + samples);
                    }

                    if (channels <= 0 || channels > MAX_CHANNELS) {
                        logger.warn("Error: channels=" + channels + " (max: " + MAX_CHANNELS + ")");
                        sendError(respHdr, client, -1);
                        continue;
                    }

                    if (samples <= 0 || samples > MAX_SAMPLES) {
                        logger.warn("Error: samples=" + samples + " (max: " + MAX_SAMPLES + ")");
                        sendError(respHdr, client, -2);
                        continue;
                    }

                    int expectedBytes = channels * samples * 4;
                    if (verbose) {
                        logger.info("Reading data: " + expectedBytes + " bytes");
                    }

                    dataBuf.clear();
                    dataBuf.limit(expectedBytes);
                    int dataBytesRead = readFully(client, dataBuf, expectedBytes);

                    if (dataBytesRead < 0) {
                        logger.warn("Client disconnected during data read");
                        break;
                    }

                    dataBuf.flip();

                    if (verbose && channels > 0 && samples > 0) {
                        FloatBuffer fb = dataBuf.asFloatBuffer();
                        logger.info("First sample (channel 0): " + fb.get(0));
                        if (channels > 1) {
                            logger.info("First sample (channel 1): " + fb.get(samples));
                        }
                    }

                    // Processing
                    FloatBuffer fb = dataBuf.asFloatBuffer();
                    switch (processMode) {
                        case "echo":
                            // Pass through unchanged
                            break;

                        case "silence":
                            // Zero out all samples
                            fb.rewind();
                            while (fb.hasRemaining()) {
                                fb.put(0.0f);
                            }
                            break;

                        case "gain":
                            // Amplify 2x - FIX: read into array first, then write
                            fb.rewind();
                            float[] gainSamples = new float[channels * samples];
                            fb.get(gainSamples);  // Read all samples

                            fb.rewind();  // Reset position for writing
                            for (int i = 0; i < gainSamples.length; i++) {
                                fb.put(gainSamples[i] * 2.0f);
                            }
                            break;

                        case "invert":
                            // Invert phase - FIX: read into array first, then write
                            fb.rewind();
                            float[] invertSamples = new float[channels * samples];
                            fb.get(invertSamples);  // Read all samples

                            fb.rewind();  // Reset position for writing
                            for (int i = 0; i < invertSamples.length; i++) {
                                fb.put(-invertSamples[i]);
                            }
                            break;

                        case "delay":
                            // Simple delay/echo effect
                            fb.rewind();
                            float[] delaySamples = new float[channels * samples];
                            fb.get(delaySamples);  // Read all samples

                            int delaySamplesCount = Math.min(samples / 8, samples - 1);

                            fb.rewind();  // Reset position for writing
                            for (int ch = 0; ch < channels; ch++) {
                                int chOffset = ch * samples;
                                for (int s = 0; s < samples; s++) {
                                    int idx = chOffset + s;
                                    float delayed = (s >= delaySamplesCount)
                                            ? delaySamples[chOffset + s - delaySamplesCount]
                                            : 0.0f;
                                    // Mix: 70% original + 30% delayed
                                    float mixed = delaySamples[idx] * 0.7f + delayed * 0.3f;
                                    fb.put(mixed);
                                }
                            }
                            break;

                        case "distort":
                            // Soft clipping distortion
                            fb.rewind();
                            float[] distortSamples = new float[channels * samples];
                            fb.get(distortSamples);  // Read all samples

                            fb.rewind();  // Reset position for writing
                            for (int i = 0; i < distortSamples.length; i++) {
                                float sample = distortSamples[i];
                                // Soft clipping
                                if (sample > 1.0f) sample = 1.0f;
                                else if (sample < -1.0f) sample = -1.0f;
                                else sample = sample * (1.0f + 0.5f * (1.0f - Math.abs(sample)));
                                fb.put(sample);
                            }
                            break;
                    }

                    // Ensure buffer is ready for sending
                    dataBuf.rewind();

                    // Send response
                    if (verbose) logger.info("Sending response...");
                    respHdr.clear();
                    respHdr.putInt(0);
                    respHdr.putInt(channels * samples);
                    respHdr.flip();

                    int hdrWritten = 0;
                    while (respHdr.hasRemaining()) {
                        hdrWritten += client.write(respHdr);
                    }
                    if (verbose) logger.info("Header sent: " + hdrWritten + " bytes");

                    dataBuf.rewind();
                    int bytesWritten = 0;
                    while (dataBuf.hasRemaining()) {
                        bytesWritten += client.write(dataBuf);
                    }

                    if (verbose) {
                        logger.info("Response sent: " + bytesWritten + " bytes");
                        logger.info("=== REQUEST PROCESSED ===\n");
                    }
                }

                if (client != null && client.isOpen()) {
                    client.close();
                    logger.info("Client socket closed");
                }

            } catch (Exception e) {
                logger.warn("Processing error: " + e.getMessage());
                if (client != null && client.isOpen()) {
                    try { client.close(); } catch (Exception ex) {}
                }
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

    private static void sendError(ByteBuffer respHdr, SocketChannel client, int errorCode) throws Exception {
        respHdr.clear();
        respHdr.putInt(errorCode);
        respHdr.putInt(0);
        respHdr.flip();
        client.write(respHdr);
        logger.warn("Sent error code: " + errorCode);
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
