import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Client
{
    private static String readResponseHeader(final InputStream inputStream) throws IOException {
        final ArrayList<Byte> list = new ArrayList<Byte>();
        byte b = 0;
        byte b2 = 0;
        byte b3 = 0;
        int read;
        while ((read = inputStream.read()) != -1) {
            final byte b4 = (byte)read;
            if (b4 == 10 && b == 13 && b2 == 10 && b3 == 13) {
                break;
            }
            list.add(b4);
            b3 = b2;
            b2 = b;
            b = b4;
        }
        final byte[] bytes = new byte[list.size()];
        for (int i = 0; i < list.size(); ++i) {
            bytes[i] = (byte)list.get(i);
        }
        return new String(bytes);
    }

    public static void main(String[] args) throws Exception
    {
        int port = Integer.parseInt(args[0]);
        Socket clientSocket = new Socket(InetAddress.getLocalHost(), port);
        DataOutputStream outToServer =  new DataOutputStream(clientSocket.getOutputStream()); 
        InputStream inFromServer = clientSocket.getInputStream();
        Scanner in = new Scanner(System.in);

        System.out.println("Enter in your initial guess: ");
        String word = in.nextLine();

        outToServer.writeBytes("POST game/ HTTP/1.0\n");
        outToServer.writeBytes("Content-Type: text/html\n");
        outToServer.writeBytes("Content-Length: " + word.length() + "\n");
        outToServer.writeBytes("Word=" + word + "\n");

        String responseHeader = readResponseHeader(inFromServer);
        ArrayList list = new ArrayList<Object>(Arrays.asList(responseHeader.split("\r\n")));
        System.out.println(list);

        String time = list.get(3).toString();
        time = time.substring(time.indexOf("=") + 1);
        String hash = list.get(4).toString();
        hash = hash.substring(hash.indexOf("=") + 1);
        String guess = list.get(5).toString();
        guess = guess.substring(guess.lastIndexOf("=") + 1); 
        System.out.println(guess);
        ArrayList<String> words = new ArrayList<String>();
        words.add(word + "=" + guess);

        while(!guess.equals("222222"))
        {
            System.out.println("Enter another guess: ");
            word = in.nextLine();
            
            String line = "";

            for(String x: words)   
                line = line + x + "&";

            line.substring(0, line.length()-1);
            line = line + "Word=" + word;

            clientSocket = new Socket(InetAddress.getLocalHost(), port);
            outToServer =  new DataOutputStream(clientSocket.getOutputStream());
            inFromServer = clientSocket.getInputStream();
            outToServer.writeBytes("POST game/ HTTP/1.0\n");
            outToServer.writeBytes("Content-Type: text/html\n");
            outToServer.writeBytes("Content-Length: " + line.length() + "\n");
            outToServer.writeBytes("Cookie: time=" + time + "; hash=" + hash + "\n");
            outToServer.writeBytes(line + "\n");

            responseHeader = readResponseHeader(inFromServer);
            list = new ArrayList<Object>(Arrays.asList(responseHeader.split("\r\n")));
            System.out.println(list);

            hash = list.get(3).toString();
            hash = hash.substring(hash.indexOf("=")+1);
            guess = list.get(4).toString();
            guess = guess.substring(guess.lastIndexOf("=") + 1); 

            System.out.println(guess);

            words.add(word + "=" + guess);
        }

        System.out.println("You guessed the word! It was " + word + ".");
        System.out.println("DONE");
        clientSocket.close(); 
        in.close();
    }
}
