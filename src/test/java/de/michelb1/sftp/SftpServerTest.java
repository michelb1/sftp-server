package de.michelb1.sftp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class SftpServerTest {

	private SftpServer server = new SftpServer();

	private static MockedStatic<SftpConfig> mockedConfig;

	@BeforeAll
	public static void initMock() {
		mockedConfig = Mockito.mockStatic(SftpConfig.class);
		mockedConfig.when(SftpConfig::getPort).thenReturn(22);
	}

	@AfterAll
	public static void closeMock() {
		if (mockedConfig != null) {
			mockedConfig.close();
		}
	}

	public void startServer() throws InterruptedException, IOException, GeneralSecurityException {
		server=new SftpServer();
		server.startServer();

        // 2. Warten, bis isRunning() true ist (Maximal 5 Sekunden)
        Duration timeout = Duration.ofSeconds(5);
        long maxTimeMs = System.currentTimeMillis() + timeout.toMillis();
        
        while (!server.isRunning()) {
            if (System.currentTimeMillis() > maxTimeMs) {
                throw new AssertionError("Timeout! Der Server ist nicht innerhalb von " 
                        + timeout.toSeconds() + " Sekunden hochgefahren.");
            }
            Thread.sleep(50); // 50 Millisekunden warten vor dem nächsten Check
        }

        // 3. Assert: Hier ist sichergestellt, dass der Server läuft
        assertTrue(server.isRunning());
	}

	@AfterEach
	public void stopServer() throws IOException, InterruptedException {
		server.stopServer();
        // 2. Warten, bis isRunning() true ist (Maximal 5 Sekunden)
        Duration timeout = Duration.ofSeconds(5);
        long maxTimeMs = System.currentTimeMillis() + timeout.toMillis();
        
        while (server.isRunning()) {
            if (System.currentTimeMillis() > maxTimeMs) {
                throw new AssertionError("Timeout! Der Server ist nicht innerhalb von " 
                        + timeout.toSeconds() + " Sekunden hochgefahren.");
            }
            Thread.sleep(50); // 50 Millisekunden warten vor dem nächsten Check
        }
	}

	//@Test
	public void testCreateSingleSubfolder(@TempDir Path testDir) throws InterruptedException, IOException, GeneralSecurityException {
		var user = "michelb1:$2a$12$AIO5VkkYfTGBoByYg4rSHeQy0rUOWDQ1zUnIBhjEMkcWlhMi069.u:" + testDir.toAbsolutePath()
				+ ":upload";
		mockedConfig.when(SftpConfig::getUsers).thenReturn(user);
		
		startServer();
		
		assertTrue(Files.exists(testDir.resolve("upload")));
	}
	
	//@Test
	public void testCreateMultipleSubfolders(@TempDir Path testDir) throws InterruptedException, IOException, GeneralSecurityException {
		var user = "michelb1:$2a$12$AIO5VkkYfTGBoByYg4rSHeQy0rUOWDQ1zUnIBhjEMkcWlhMi069.u:" + testDir.toAbsolutePath()
				+ ":upload,download";
		mockedConfig.when(SftpConfig::getUsers).thenReturn(user);
		
		startServer();
		
		assertTrue(Files.exists(testDir.resolve("upload")));
	}

	//@Test
	public void testCreateNoSubfolders(@TempDir Path testDir) throws InterruptedException, IOException, GeneralSecurityException {
		var user = "michelb1:$2a$12$AIO5VkkYfTGBoByYg4rSHeQy0rUOWDQ1zUnIBhjEMkcWlhMi069.u:" + testDir.toAbsolutePath();
		mockedConfig.when(SftpConfig::getUsers).thenReturn(user);
		
		startServer();
		
		assertTrue(Files.exists(testDir));
	}
}
