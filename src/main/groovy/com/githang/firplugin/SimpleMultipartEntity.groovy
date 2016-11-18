package com.githang.firplugin

import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.message.BasicHeader
/**
 * 以下代码参考自文章：http://blog.rafaelsanches.com/2011/01/29/upload
 * -using-multipart-post-using-httpclient-in-android/<br/>
 * Multipart/form coded HTTP entity consisting of multiple body parts.
 *
 */
public class SimpleMultipartEntity implements HttpEntity {

    /**
     * ASCII的字符池，用于生成分界线。
     */
    private final static char[] MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            .toCharArray();

    private ByteArrayOutputStream out;
    /**
     * 是否设置了开头的分界线
     */
    boolean isSetFirst = false;
    /**
     * 是否设置了最后的分界线
     */
    boolean isSetLast = false;

    /**
     * 分界线
     */
    private String boundary;

    private String charset = "UTF-8";

    public SimpleMultipartEntity() {
        out = new ByteArrayOutputStream();
        boundary = generateBoundary();
    }

    /**
     * 生成分界线
     *
     * @return 返回生成的分界线
     */
    protected String generateBoundary() {
        StringBuilder buffer = new StringBuilder();
        Random rand = new Random();
        int count = rand.nextInt(11) + 30; // a random size from 30 to 40
        for (int i = 0; i < count; i++) {
            buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
        }
        return buffer.toString();
    }

    public void writeFirstBoundaryIfNeeds() {
        if (!isSetFirst) {
            try {
                out.write(("--" + boundary + "\r\n").getBytes(charset));
            } catch (final IOException e) {
                e.printStackTrace()
            }
        }
        isSetFirst = true;
    }

    public void writeLastBoundaryIfNeeds() {
        if (!isSetLast) {
            try {
                out.write(("\r\n--" + boundary + "--\r\n").getBytes(charset));
            } catch (final IOException e) {
                e.printStackTrace()
            }
            isSetLast = true;
        }
    }

    public void addPart(final String key, final String value) {
        writeFirstBoundaryIfNeeds();
        try {
            out.write(("Content-Disposition: form-data; name=\"" + key + "\"\r\n\r\n").getBytes(charset));
            out.write(value.getBytes(charset));
            out.write(("\r\n--" + boundary + "\r\n").getBytes(charset));
        } catch (final IOException e) {
            e.printStackTrace();
        }
    }

    public void addPart(final String key, final String fileName, final InputStream fin,
                        final boolean isLast) {
        addPart(key, fileName, fin, "application/octet-stream", isLast);
    }

    public void addPart(final String key, final String fileName, final InputStream fin,
                        String type, final boolean isLast) {
        writeFirstBoundaryIfNeeds();
        try {
            type = "Content-Type: " + type + "\r\n";
            out.write(("Content-Disposition: form-data; name=\"" + key + "\"; filename=\""
                    + fileName + "\"\r\n").getBytes(charset));
            out.write(type.getBytes(charset));
            out.write("Content-Transfer-Encoding: binary\r\n\r\n".getBytes(charset));

            final byte[] tmp = new byte[4096];
            int l = 0;
            while ((l = fin.read(tmp)) != -1) {
                out.write(tmp, 0, l);
            }
            if (!isLast)
                out.write(("\r\n--" + boundary + "\r\n").getBytes(charset));
            out.flush();
        } catch (final IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fin.close();
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addPart(final String key, final File value, final boolean isLast) {
        try {
            addPart(key, value.getName(), new FileInputStream(value), isLast);
        } catch (final FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public long getContentLength() {
        writeLastBoundaryIfNeeds();
        return out.toByteArray().length;
    }

    @Override
    public Header getContentType() {
        return new BasicHeader("Content-Type", "multipart/form-data; boundary=" + boundary);
    }

    @Override
    public boolean isChunked() {
        return false;
    }

    @Override
    public boolean isRepeatable() {
        return false;
    }

    @Override
    public boolean isStreaming() {
        return false;
    }

    @Override
    public void writeTo(final OutputStream outstream) throws IOException {
        outstream.write(out.toByteArray());
    }

    @Override
    public Header getContentEncoding() {
        return null;
    }

    @Override
    public void consumeContent() throws IOException, UnsupportedOperationException {
        if (isStreaming()) {
            throw new UnsupportedOperationException(
                    "Streaming entity does not implement #consumeContent()");
        }
    }

    @Override
    public InputStream getContent() throws IOException, UnsupportedOperationException {
        return new ByteArrayInputStream(out.toByteArray());
    }
}