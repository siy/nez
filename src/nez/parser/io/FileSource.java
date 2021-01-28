package nez.parser.io;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;

import nez.util.StringUtils;
import nez.util.Verbose;

public class FileSource extends CommonSource {
	public static final int PageSize = 4096;

	private final RandomAccessFile file;
	private final long fileLength;
	private long buffer_offset;
	private byte[] buffer;
	private final long[] lines;

	private final int FifoSize = 8;
	private final LinkedHashMap<Long, byte[]> fifoMap;

	public FileSource(String fileName) throws IOException {
		super(fileName, 1);
		try {
			this.file = new RandomAccessFile(fileName, "r");
			this.fileLength = file.length();

			this.buffer_offset = 0;
			lines = new long[((int) fileLength / PageSize) + 1];
			lines[0] = 1;
			this.fifoMap = new LinkedHashMap<>(FifoSize) { // FIFO
				private static final long serialVersionUID = 6725894996600788028L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
					return size() > FifoSize;
				}
			};
			this.buffer = null;
			readMainBuffer(buffer_offset);
		} catch (Exception e) {
			Verbose.traceException(e);
			throw new IOException(e.getMessage());
		}
	}

	@Override
	public final long length() {
		return fileLength;
	}

	private long buffer_alignment(long pos) {
		return (pos / PageSize) * PageSize;
	}

	@Override
	public final int byteAt(long pos) {
		int buffer_pos = (int) (pos - buffer_offset);
		if (!(buffer_pos >= 0 && buffer_pos < PageSize)) {
			this.buffer_offset = buffer_alignment(pos);
			readMainBuffer(buffer_offset);
			buffer_pos = (int) (pos - buffer_offset);
		}
		return buffer[buffer_pos] & 0xff;
	}

	@Override
	public final boolean eof(long pos) {
		return pos >= length(); //
	}

	@Override
	public final boolean match(long pos, byte[] text) {
		int offset = (int) (pos - buffer_offset);
		if (offset >= 0 && offset + text.length <= PageSize) {
			switch (text.length) {
			case 0:
				break;
			case 1:
				return text[0] == buffer[offset];
				case 2:
				return text[0] == buffer[offset] && text[1] == buffer[offset + 1];
				case 3:
				return text[0] == buffer[offset] && text[1] == buffer[offset + 1] && text[2] == buffer[offset + 2];
				case 4:
				return text[0] == buffer[offset] && text[1] == buffer[offset + 1] && text[2] == buffer[offset + 2]
					&& text[3] == buffer[offset + 3];
				default:
				for (int i = 0; i < text.length; i++) {
					if (text[i] != buffer[offset + i]) {
						return false;
					}
				}
			}
			return true;
		}
		for (int i = 0; i < text.length; i++) {
			if ((text[i] & 0xff) != byteAt(pos + i)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public final String subString(long startIndex, long endIndex) {
		if (endIndex > startIndex) {
			try {
				long off_s = buffer_alignment(startIndex);
				long off_e = buffer_alignment(endIndex);
				if (off_s == off_e) {
					if (buffer_offset != off_s) {
						this.buffer_offset = off_s;
						readMainBuffer(buffer_offset);
					}
					return new String(buffer, (int) (startIndex - buffer_offset), (int) (endIndex - startIndex), StringUtils.DefaultEncoding);
				} else {
					byte[] b = new byte[(int) (endIndex - startIndex)];
					readStringBuffer(startIndex, b);
					return new String(b, StringUtils.DefaultEncoding);
				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return "";
	}

	@Override
	public final byte[] subByte(long startIndex, long endIndex) {
		byte[] b = null;
		if (endIndex > startIndex) {
			long off_s = buffer_alignment(startIndex);
			long off_e = buffer_alignment(endIndex);
			b = new byte[(int) (endIndex - startIndex)];
			if (off_s == off_e) {
				if (buffer_offset != off_s) {
					this.buffer_offset = off_s;
					readMainBuffer(buffer_offset);
				}
				System.arraycopy(buffer, (int) (startIndex - buffer_offset), b, 0, b.length);
			} else {
				readStringBuffer(startIndex, b);
			}
		}
		return b;
	}

	private int lineIndex(long pos) {
		return (int) (pos / PageSize);
	}

	private long startLineNum(long pos) {
		int index = lineIndex(pos);
		return lines[index];
	}

	@Override
	public final long linenum(long pos) {
		long count = startLineNum(pos);
		byteAt(pos); // restore buffer at pos
		int offset = (int) (pos - buffer_offset);
		for (int i = 0; i < offset; i++) {
			if (buffer[i] == '\n') {
				count++;
			}
		}
		return count;
	}

	private void readMainBuffer(long pos) {
		int index = lineIndex(pos);
		if (lines[index] == 0) {
			long count = lines[index - 1];
			for (byte b : buffer) {
				if (b == '\n') {
					count++;
				}
			}
			lines[index] = count;
		}
		if (fifoMap != null) {
			Long key = pos;
			byte[] buf = fifoMap.get(key);
			if (buf == null) {
				buf = new byte[PageSize];
				readBuffer(pos, buf);
				fifoMap.put(key, buf);
			}
			this.buffer = buf;
		} else {
			readBuffer(pos, buffer);
		}
	}

	private void readBuffer(long pos, byte[] b) {
		try {
			file.seek(pos);
			int readsize = file.read(b);
			for (int i = readsize; i < b.length; i++) {
				b[i] = 0;
			}
		} catch (Exception e) {
			Verbose.traceException(e);
		}
	}

	private void readStringBuffer(long pos, byte[] buf) {
		if (fifoMap != null) {
			int copied = 0;
			long start = pos;
			long end = pos + buf.length;
			while (start < end) {
				long offset = buffer_alignment(start);
				if (buffer_offset != offset) {
					this.buffer_offset = offset;
					readMainBuffer(offset);
				}
				int start_off = (int) (start - offset);
				int end_off = (int) (end - offset);
				if (end_off <= PageSize) {
					int len = end_off - start_off;
					System.arraycopy(buffer, start_off, buf, copied, len);
					copied += len;
					assert (copied == buf.length);
					return;
				} else {
					int len = PageSize - start_off;
					System.arraycopy(buffer, start_off, buf, copied, len);
					copied += len;
					start += len;
				}
			}
		} else {
			readBuffer(pos, buf);
		}
	}

}