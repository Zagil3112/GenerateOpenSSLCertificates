package com.example.generatecertificates;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.Observable;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

@Slf4j
public class RuntimeExecutor {

    private ProcessBuilder builder = new ProcessBuilder();
    public Process process;
    private StreamReader streamReader;
    private StringBuilder logStr = new StringBuilder();
    private StringBuilder logErr = new StringBuilder();

    public String getLog() {
        return logStr.toString();
    }

    public String getErrLog() {
        return logErr.toString();
    }

    public StreamReader exec(String command, String home) throws IOException {
        if (isWindows()) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }

        builder.directory(new File(home != null ? home : System.getProperty("user.home")));
        process = builder.start();

        //process.getOutputStream().write("12345678".getBytes(StandardCharsets.UTF_8));

        streamReader = new StreamReader(process.getInputStream(), (msg) -> {
            logStr.append(msg);
        }, (msg) -> {
            logErr.append(msg);
        }, process);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.submit(streamReader);
        executorService.shutdown();
        return streamReader;
    }

    public StreamReader getStreamReader() {
        return streamReader;
    }

    public boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
    }

    public static class StreamReader extends Observable implements Callable<Integer> {
        private InputStream inputStream;
        private Consumer<String> consumer;
        private Consumer<String> consumerErr;
        private Process process;
        private InputStream errorStream;

        public StreamReader(InputStream inputStream, Consumer<String> consumer, Consumer<String> consumerErr, Process process) {
            this.inputStream = inputStream;
            this.consumer = consumer;
            this.consumerErr = consumerErr;
            this.process = process;
            errorStream = this.process.getErrorStream();
        }

        @Override
        public Integer call() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach((val) -> {
                        consumer.accept(val);
                    });

            new BufferedReader(new InputStreamReader(errorStream)).lines()
                    .forEach((val) -> {
                        consumerErr.accept(val);
                    });
            try {
                int result = process.waitFor();
                setChanged();
                notifyObservers(result);
                System.out.println();
                return result;
            } catch (InterruptedException e) {
                e.printStackTrace();
                return -1;
            }
        }

        public void stop() {
            try {
                if (process != null) {
                    process.destroyForcibly();
                }
                process = null;
            } catch (Exception ex) {
                throw new IllegalArgumentException(ex);
            } finally {
            }
        }
    }

    /*public static void main(String[] args) {
        RuntimeExecutor runtimeExecutor = new RuntimeExecutor();

        try {
            runtimeExecutor.exec("./nodetool refresh fac_radar_data h_raw_data", "/Users/jorgealzate/cassandra/apache-cassandra-3.11.10/bin");
            runtimeExecutor.getStreamReader().addObserver((o, result) -> {
                Integer resultCode = (Integer) result;
                log.debug(String.format("El resultado fue de %d", resultCode));
            });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }*/
}
