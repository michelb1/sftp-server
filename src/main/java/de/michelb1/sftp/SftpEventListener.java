package de.michelb1.sftp;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.Map;

import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.AbstractSftpEventListenerAdapter;

public class SftpEventListener extends AbstractSftpEventListenerAdapter{

	@Override
	public void removing(ServerSession session, Path path, boolean isDirectory) throws IOException {
		if (isDirectory && !SftpConfig.getBooleanValue(SftpConfigKey.SFTP_DELETE_FOLDER_PERMISSION)) {
			throw new AccessDeniedException("Delete operation is forbidden.");
		}
		super.removing(session, path, isDirectory);
	}

	@Override
	public void creating(ServerSession session, Path path, Map<String, ?> attrs) throws IOException {
		if (!SftpConfig.getBooleanValue(SftpConfigKey.SFTP_CREATE_FOLDER_PERMISSION)) {
			throw new AccessDeniedException("Directory creation is forbidden.");
		}
		super.creating(session, path, attrs);
	}
	
}
