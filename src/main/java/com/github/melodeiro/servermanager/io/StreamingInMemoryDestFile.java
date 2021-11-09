package com.github.melodeiro.servermanager.io;

import net.schmizz.sshj.xfer.InMemoryDestFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * Created by Daniel on 11.03.2017.
 * @author Melodeiro
 */
class StreamingInMemoryDestFile extends InMemoryDestFile {

    private ArrayList<Byte> byteArray = new ArrayList<>();
    private OutputStream outputStream;

    StreamingInMemoryDestFile() {
        this.outputStream = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                byteArray.add((byte) b);
            }
        };
    }

    String getResult() {
        byte[] result = new byte[byteArray.size()];
        for (int i = 0; i < byteArray.size(); i++)
            result[i] = byteArray.get(i);
        return new String(result, Charset.forName("UTF8"));
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return this.outputStream;
    }

}