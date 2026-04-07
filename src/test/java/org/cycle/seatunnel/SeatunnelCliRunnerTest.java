package org.cycle.seatunnel;

import org.cycle.seatunnel.config.SeatunnelProperties;
import org.cycle.seatunnel.runtime.SeatunnelCliRunner;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SeatunnelCliRunnerTest {

    @Test
    void start_shouldWriteLog() throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase();
        assertTrue(os.contains("win"));

        Path home = Files.createTempDirectory("seatunnel-home-");
        Path bin = Files.createDirectories(home.resolve("bin"));
        Path bat = bin.resolve("seatunnel.bat");

        String script = "@echo off\r\n" +
                "echo JobId: 123e4567-e89b-12d3-a456-426614174000\r\n" +
                "exit /B 0\r\n";
        Files.write(bat, script.getBytes(StandardCharsets.UTF_8));

        SeatunnelProperties props = new SeatunnelProperties();
        props.setHome(home.toString());

        SeatunnelCliRunner runner = new SeatunnelCliRunner(props);

        Path runDir = Files.createTempDirectory("seatunnel-run-");
        Path config = runDir.resolve("job.conf");
        Path log = runDir.resolve("seatunnel.log");
        Files.write(config, "env {}".getBytes(StandardCharsets.UTF_8));
        Files.createFile(log);

        Process p = runner.start(config, log, "local", "");
        int exit = p.waitFor();
        assertTrue(exit == 0);

        String logText = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
        assertTrue(logText.contains("JobId"));
    }
}
