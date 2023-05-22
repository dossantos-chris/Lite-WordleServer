import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.math.*;
import java.util.*;
import java.util.concurrent.*;

public class WordGameServer 
{
    private static final ArrayList<String> IMPLMENTED_COMMANDS = new ArrayList<String>(Arrays.asList("POST"));
    private static final ArrayList<String> UNIMPLMENTED_COMMANDS = new ArrayList<String>(Arrays.asList("GET", "HEAD", "DELETE", "PUT", "LINK", "UNLINK"));
    private static final BigInteger DICT_LENGTH = new BigInteger("23028");
    protected static int threads = 0;

    public static void main(String[] args) throws Exception
    {
        //int port = Integer.parseInt(args[0]);
        ServerSocket serverSocket = new ServerSocket(7406);
        
        while(true) 
        {
            Socket clientSocket = serverSocket.accept();

            if(threads == 50)
            {
                DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream()); 
                outToClient.writeBytes("HTTP/1.0 503 Service Unavailable\r\n");
                outToClient.flush();
                TimeUnit.MILLISECONDS.sleep(250);
                outToClient.close();
                clientSocket.close();
            }
            else
            {
                ServerThread thread = new ServerThread(clientSocket);
                new Thread(thread).start();
            }
        }
    }

    private static class ServerThread implements Runnable
    {
        private final Socket clientSocket;

        public ServerThread(Socket clientSocket)
        {
            this.clientSocket = clientSocket;
            threads++;
        }

        public void run()
        {
            try
            {
                clientSocket.setSoTimeout(5000);
                BufferedReader inFromClient = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); 
                DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream()); 
                
                String[] request;

                try
                {
                    request = inFromClient.readLine().split(" ");
                }
                catch(SocketTimeoutException e)
                {
                    outToClient.writeBytes("HTTP/1.0 408 Request Timeout\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                if(request.length != 3)
                {
                    outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                String command = request[0];
                String resource = request[1];
                String version = request[2];
                
                if(!IMPLMENTED_COMMANDS.contains(command) && !UNIMPLMENTED_COMMANDS.contains(command))
                {
                    outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                if(UNIMPLMENTED_COMMANDS.contains(command))
                {
                    outToClient.writeBytes("HTTP/1.0 501 Not Implemented\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                if(!version.equals("HTTP/1.0"))
                {
                    outToClient.writeBytes("HTTP/1.0 505 HTTP Version Not Supported\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }
                
                if(!resource.equals("game/"))
                {
                    outToClient.writeBytes("HTTP/1.0 404 Not Found\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }
                
                inFromClient.readLine();
                inFromClient.readLine();
                String line = inFromClient.readLine();
                Scanner scanner = new Scanner(new File("secret.txt"));
                String hexStr = scanner.nextLine();
                scanner.close();
                BigInteger key = new BigInteger(hexStr, 16);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long time = 0;
                boolean firstFlag = false;
                String userHash = "";

                if(line.contains("Cookie"))
                {
                    String[] toks = line.split(" ");
                    String temp = toks[1].substring(toks[1].indexOf("=")+1, toks[1].length()-1);
                    userHash = toks[2].substring(toks[2].indexOf("hash=")+5);
                    time = Long.parseLong(temp);
                    line = inFromClient.readLine();
                }
                else if(line.contains("Word"))
                {
                    Clock clock = Clock.systemDefaultZone();
                    time = clock.millis();
                    firstFlag = true;
                }
                else
                {
                    outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                BigInteger timeInt = BigInteger.valueOf(time);
                BigInteger cookie = key.add(timeInt);
                String hex = cookie.toString(16);
                byte[] bytes = digest.digest(hex.getBytes(StandardCharsets.UTF_8));
                BigInteger hash = new BigInteger(bytes);
                BigInteger wordLine = hash.mod(DICT_LENGTH);
                String guess = line.substring(line.indexOf("Word=")+5).toUpperCase();

                scanner = new Scanner(new File("dictionary.txt"));
                String word = "";
                for(int i = 0; i < wordLine.intValue(); i++)
                    word = scanner.nextLine();

                String check = "000000";
                for(int i = 0; i < guess.length(); i++)
                {
                    if(word.indexOf(guess.charAt(i)) == i)
                    {
                        check = check.substring(0, i) + "2" + check.substring(i+1);
                        word = word.substring(0, i) + " " + word.substring(i+1);
                    }
                }

                for(int i = 0; i < guess.length(); i++)
                {
                    if((check.charAt(i) != '2') && (word.indexOf(guess.charAt(i)) != -1))
                    {
                     check = check.substring(0, i) + "1" + check.substring(i+1);
                    }
                }
                
                String response = line.substring(0, line.indexOf("Word="));
                response += guess.toLowerCase() + "=";
                response += check;

                String toks[] = response.split("&");
                String temp = "";
                Boolean auth = false;

                if(toks.length == 1)
                {
                    auth = true;
                }
                else
                {
                    for(int i = 1; i < toks.length; i++)
                    {  
                        for(int j = 0; j < i; j++)
                        {
                            temp += toks[j];
                            temp += "&";
                        }
                        temp = temp.substring(0, temp.length()-1);
                        bytes = digest.digest((temp + hash.toString(16)).getBytes(StandardCharsets.UTF_8));
                        hash = new BigInteger(bytes);
                        temp = "";

                        if(hash.toString(16).equals(userHash))
                        {
                            auth = true;
                        }
                    }
                }

                if(!auth)
                {
                    outToClient.writeBytes("HTTP/1.0 400 Bad Request\r\n");
                    outToClient.flush();
                    TimeUnit.MILLISECONDS.sleep(250);
                    outToClient.close();
                    inFromClient.close();
                    clientSocket.close();
                    threads--;
                    return;
                }

                bytes = digest.digest((response + hash.toString(16)).getBytes(StandardCharsets.UTF_8));
                hash = new BigInteger(bytes);

                outToClient.writeBytes("HTTP/1.0 200 OK\r\n");
                outToClient.writeBytes("Content-Type: text/html\r\n");
                outToClient.writeBytes("Content-Length: " + response.length() + "\r\n");
                if(firstFlag)
                    outToClient.writeBytes("Set-Cookie: time=" + time + "\r\n");

                outToClient.writeBytes("Set-Cookie: hash=" + hash.toString(16) + "\r\n");
                outToClient.writeBytes(response + "\r\n");
                System.out.println(word); //
                outToClient.flush();
                TimeUnit.MILLISECONDS.sleep(250);
                outToClient.close();
                inFromClient.close();
                clientSocket.close();
                threads--;
                return;
            }
            catch(Exception e)
            {
                try 
                {
                    DataOutputStream outToClient = new DataOutputStream(clientSocket.getOutputStream());
                    outToClient.writeBytes("500 Internal Server Error\r\n");
                    threads--;
                    return;
                } 
                catch (IOException e1)
                {
                    threads--;
                    return;
                }
            }
        }
    }
}