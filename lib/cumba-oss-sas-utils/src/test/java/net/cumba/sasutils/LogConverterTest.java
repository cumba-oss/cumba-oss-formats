package net.cumba.sasutils;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class LogConverterTest
{

    private static final Logger LOGGER = LoggerFactory.getLogger(LogConverterTest.class);

    @SuppressWarnings(
    {
            "resource", "unused"
    })
    @Test
    void test() throws URISyntaxException, IOException, DecoderException
    {
        List<byte[]> bytes = new ArrayList<>();
        BufferedReader br = new BufferedReader(
                new FileReader(new File(LogConverterTest.class.getResource("v8log.txt").toURI()),
                        StandardCharsets.UTF_8));

        File outFile = new File(System.getProperty("projectBasedir"), "target/v8file.xpt");
        String line;
        while ((line = br.readLine()) != null)
        {
            String[] parts = line.split("( )+", 2);
            LOGGER.info("parts: {}", new Object[]
            {
                    parts
            });
            try
            {
                Integer num = Integer.valueOf(parts[0]);
                if (parts[1].startsWith("CHAR"))
                {
                    String hex1 = br.readLine().split("( )+", 0)[2];
                    String hex2 = br.readLine().split("( )+", 0)[2];
                    StringBuilder hexString = new StringBuilder();
                    for (int i = 0; i < hex1.length(); i++)
                    {
                        hexString.append(hex1.charAt(i));
                        hexString.append(hex2.charAt(i));
                    }
                    LOGGER.info("hex: {}", hexString);
                    byte[] ar = Hex.decodeHex(hexString.toString());
                    bytes.add(ar);
                }
                else
                {
                    String fullLine = StringUtils.rightPad(parts[1], 80);
                    LOGGER.info("line: {}", fullLine);
                    bytes.add(fullLine.getBytes(StandardCharsets.UTF_8));
                }
            }
            catch (NumberFormatException _)
            {
                // Ignore non-numeric header lines — only numbered data rows are processed.
            }

            FileOutputStream fos = new FileOutputStream(outFile);

            for (byte[] b : bytes)
            {
                fos.write(b);
            }
            fos.flush();
            fos.close();
        }

        assertTrue(outFile.exists(), "Expected converted xpt file to be written");
    }

}
