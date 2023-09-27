import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Reti e Laboratorio III - A.A. 2022/2023
 * WORDLE: Un gioco di parole 3.0
 *
 * Classe Java che rappresenta il server del gioco.
 * Il server prende le impostazioni di inizializzazione dal file server.properties,
 * Costruisce un FixedThreadPool dedicata alla gestione dei vari client in connessione,
 * Costruisce uno ScheduledExecutorService dedicato al cambio periodico della parola da indovinare.
 * 
 * Dopo aver letto il file server.properties, costruisce il gruppo multicast su cui ogni thread WordleGame
 * manderanno le notifiche delle condivisioni dei client.
 * Dopodichè entra in un ciclo while dove si blocca sulla accept() fino a nuova connessione con un client.
 *
 */

public class MainWordleServer {
    // Percorso del file di configurazione del server.
    public static final String configFile = "server.properties";
    // Porta di ascolto del server.
    public static int port;
    // Porta e indirizzo di ascolto del gruppo multicast.
    public static int datagramPort;
    public static String datagramAddress;
    // Tempo massimo di attesa (in ms) per la terminazione del server.
    public static int maxDelay;
    public static int delay;
    // Pool di thread.
    public static ExecutorService pool = Executors.newFixedThreadPool(5);
    // Thread per la schedulazione del cambio di parola.
    public static ScheduledExecutorService scheduleWordSelection =Executors.newSingleThreadScheduledExecutor();

    // Socket per ricevere le richieste dei client.
    public static ServerSocket serverSocket;
    public static DatagramSocket datagramSocket;
    // Gruppo multicast.
    public static InetAddress group;
    public static WordManager wordManager;

    public static void main(String[] args)  {
        try {
            // Leggo il file di configurazione.
            readConfig();
            wordManager = new WordManager();
            // Apro la ServerSocket e resto in attesa di richieste.
            serverSocket = new ServerSocket(port);
            datagramSocket = new DatagramSocket();
            // Definisco il gruppo multicast.
            group = InetAddress.getByName(datagramAddress);
            // Lancio un'errore se non è un indirizzo multicast valido.
            if (!group.isMulticastAddress()) {
                throw new IllegalArgumentException("[SERVER] Indirizzo multicast non valido: " + group.getHostAddress());
            }
            // Avvio l'handler di terminazione.
            Runtime.getRuntime().addShutdownHook(new TerminationHandler(maxDelay, pool, serverSocket));
            System.out.printf("[SERVER] In ascolto sulla porta: %d\n", port);
            // Avvio lo scheduler per il cambio di parola
            clock();

            while (true) {
                Socket socket;
                // Accetto le richieste provenienti dai client.
                // Quando il TerminationHandler chiude la ServerSocket
                // viene sollevata una SocketException ed esco dal ciclo.
                try {socket = serverSocket.accept();}
                catch (SocketException e) {break;}
                // Avvio un WordleGame per interagire con il client.
                pool.execute(new WordleGame(socket, datagramSocket, datagramPort, group, wordManager));
            }
        }
        catch (Exception e) {
            System.err.printf("[SERVER] Errore: %s\n", e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Metodo che legge il file di configurazione del server.
     * @throws FileNotFoundException se il file non esiste
     * @throws IOException se si verifica un errore durante la lettura
     */

    public static void readConfig() throws FileNotFoundException, IOException {
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        port = Integer.parseInt(prop.getProperty("port"));
        maxDelay = Integer.parseInt(prop.getProperty("maxDelay"));
        datagramAddress = prop.getProperty("datagramAddress");
        datagramPort = Integer.parseInt(prop.getProperty("datagramPort"));
        delay = Integer.parseInt(prop.getProperty("wordDelay"));
        input.close();
    }

    /**
     * Metodo che avvia lo scheduler che cambia la parola ogni 60 minuti.
     *
     */

    public static void clock(){
        scheduleWordSelection.scheduleWithFixedDelay(wordManager, 0, delay, TimeUnit.SECONDS);
    }
}