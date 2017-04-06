package de.stonelabs.fastshare;

import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import de.stonelabs.fastshare.main;

class FileTransmitter implements Runnable
{
    public enum Status
    {
        INITIALIZED,
        STARTED,
        EXCEPTION,
        FAILURE,
        SUCCESS
    }
    private Status status = Status.INITIALIZED;
    private String details = "";
    private String response = "";
    private float progress = 0.0f;
    private final String fileName;
    private final String key;


    public FileTransmitter(String file, String key)
    {
        this.fileName = file;
        this.key = key;
    }

    private void setStatus(Status status, String details)
    {
        this.status = status;
        this.details = details;
    }

    public Status getStatus() {return status;}
    public String getDetails() {return details;}
    public float getProgress() {return progress;}
    public String getResponse() { return response;}

    public void run()
    {
        setStatus(Status.STARTED, "");

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead;
        byte[] buffer;
        int maxBufferSize = 1024;
        File sourceFile = new File(fileName);
        long byteLength = sourceFile.length();
        long currentPos = 0;
        int serverResponseCode = 0;

        if (!sourceFile.isFile())
            setStatus(Status.FAILURE, "Source File not exist :" + fileName);
        else
        {
            try {
                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL("http://ni18061_5.vweb10.nitrado.net/interface/app/upload.php?key=" + key);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setChunkedStreamingMode(maxBufferSize);
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                //conn.setRequestProperty("Authorization", "Basic YWNjZXNzOmdyYW50ZWQ=");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("uploaded_file", fileName);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""
                        + fileName + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                buffer = new byte[maxBufferSize];

                // read file and write it into form...
                while ((bytesRead = fileInputStream.read(buffer)) > 0)
                {
                    progress = ((currentPos += bytesRead) / (float)byteLength) * 100.0f;
                    dos.write(buffer, 0, bytesRead);
                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                BufferedReader bufferedReader;
                if(serverResponseCode == 200)
                {
                    setStatus(Status.SUCCESS, serverResponseMessage + ": " + serverResponseCode);
                    bufferedReader = new BufferedReader(new InputStreamReader((conn.getInputStream())));
                }
                else
                {
                    setStatus(Status.FAILURE, serverResponseMessage + ": " + serverResponseCode);
                    bufferedReader = new BufferedReader(new InputStreamReader((conn.getErrorStream())));
                }

                StringBuilder stringBuilder = new StringBuilder();
                String responseBody;
                while ((responseBody = bufferedReader.readLine()) != null)
                    stringBuilder.append(responseBody);

                response = stringBuilder.toString();

                //close the streams //
                fileInputStream.close();
                dos.flush();
                dos.close();

            } catch (MalformedURLException e) {
                setStatus(Status.EXCEPTION, "NETWORK EXCEPTION: " + (e.getMessage().toString() == null ? "UNKNOWN" : e.getMessage().toString()));
            } catch (Exception e) {
                setStatus(Status.EXCEPTION, "EXCEPTION: " + (e.getMessage().toString() == null ? "UNKNOWN" : e.getMessage().toString()));
            }
        } // End else block
    }
}