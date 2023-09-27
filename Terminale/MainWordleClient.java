import java.io.*;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.util.Properties;
import java.util.Scanner;

/**
 * Reti e Laboratorio III - A.A. 2022/2023
 * WORDLE: Un gioco di parole 3.0
 *
 * Classe Java che rappresenta il client del gioco.
 *
 * Il client esegue un ciclo in cui:
 * (1) Esegue il comando registerOrLogin per la registrazione e autentificazione dell'utente;
 * (2) Solo dopo essersi autentificato il client ha accesso a tutti gli altri comandi del gioco (lobby);
 * (3) Con il comando "play" il client può accedere al tentativo della parola corrente
 *     solo dopo un controllo sull'eventuale partecipazione già effettuata;
 * (4) Al termine del tentativo che sia vinto o perso, il client "torna nella lobby" dove avrà accesso a tutti gli altri comandi di nuovo.
 *
 * Le varie fasi del ciclo si susseguono a seconda dello status in cui si trova l'utente,
 * il quale si orienta grazie a vari messaggi inviati al server.
 *
 * I comandi supportati dal client (registerOrLogin):
 * (1) register: registra nome utente e password;
 * (2) login: autentifica nome utente e password;
 * (3) exit: chiude l'applicazione. Di conseguenza si avvia la terminazione del client.
 *
 * e.g. [comando] [username] [password]
 *
 * I comandi supportati dal client (lobby):
 * (1) play: inizia il tentativo;
 * (2) mostrastat: visualizza le statistiche del giocatore;
 * (3) condividi: condivisione dell'ultima partita agli altri giocatori;
 * (4) mostrafeed: visualizza le notifiche condivise dagli altri giocatori;
 * (5) logout: esce dal profilo corrente.
 *
 *
 */

public class MainWordleClient {
    // Percorso del file di configurazione del client.
    public static final String configFile = "client.properties";
    // Variabile globale che rappresenta lo stato corrente.
    public static Status status = Status.NOTLOGGED;

    // Nome host, porta del server.
    public static String hostname;
    public static int port;

    // Indirizzo e porta del gruppo multicast.
    public static String multicastAddress;
    public static int multicastPort;
    // Socket, Multicast Socket e relativi stream di input/output.
    private static final Scanner scanner = new Scanner(System.in);
    private static Socket socket;
    private static MulticastSocket multicastSocket;
    private static BufferedReader in;
    private static PrintWriter out;

    public static void main(String[] args) {
        try {
            // Leggo il file di configurazione.
            readConfig();
            // Apro la socket e gli stream di input e output.
            socket = new Socket(hostname, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            // Entro nel ciclo principale.
            while (true) {
                // Fase di registrazione/autentificazione.
                lobby();
                //Terminazione del client al comando "exit".
                if (status == Status.INTERRUPTED) break;
                // Autentificazione effettuata. Comandi disponibili messi a schermo.
                commads();
                // Inizio della partita di Wordle.
                game();
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.err.printf("Errore: %s\n", e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Metodo per leggere il file di configurazione del client.
     *
     * @throws FileNotFoundException se il file non e' presente
     * @throws IOException           se qualcosa non va in fase di lettura
     */
    public static void readConfig() throws FileNotFoundException, IOException {
        InputStream input = new FileInputStream(configFile);
        Properties prop = new Properties();
        prop.load(input);
        hostname = prop.getProperty("hostname");
        multicastAddress = prop.getProperty("multicastAddress");
        port = Integer.parseInt(prop.getProperty("port"));
        multicastPort = Integer.parseInt(prop.getProperty("multicastPort"));
        input.close();
    }

    /**
     * Gestisce l'interazione con il server durante la fase di registrazione e autentificazione.
     *
     * @throws IOException nel caso in cui vi sia un errore di comunicazione
     */
    public static void lobby() throws IOException {
        if (status == Status.NOTLOGGED)
            System.out.println
                    ("""
                            Registrati per poter iniziare a giocare! Sei già registrato, effettua il login per accedere ai servizi di gioco!
                            | Comandi disponibili: |
                            Register | Registrazione nome e password utente.
                            Login | Autentificazione nome e password utente.
                            Exit | Uscita dall'applicazione.""");
        while (status == Status.NOTLOGGED) {
            System.out.printf("> ");
            // Leggo il comando da tastiera.
            String command = scanner.nextLine();
            // Se il comando è "exit" aggiorno lo stato ed interrompo il ciclo.
            if (command.equalsIgnoreCase("exit")){
                status = Status.INTERRUPTED;
                System.out.println("Chiudo l'applicazione.");
                break;
            }
            // Lo invio al server.
            out.println(command);
            // Attendo la risposta dal server.
            String reply = in.readLine();
            // Elaboro la risposta, aggiornando lo stato.
            switch (reply) {
                case "registered":
                    System.out.println("Registrazione Utente effettuata.");
                    break;
                case "logged":
                    status = Status.LOGGED;
                    System.out.println("Login Utente effettuato." + "\n");
                    receiveFeeds();
                    break;
                default:
                    System.out.println(reply);
                    break;
            }
        }
    }

    /**
     * Gestisce l'interazione con il server per i vari comandi disponibili dopo autentificazione.
     *
     * @throws IOException nel caso in cui vi sia un errore di comunicazione
     */

    public static void commads() throws IOException {
        System.out.printf("""
                -- Comandi disponibili: --
                | PLAY: Inizia una partita di WORDLE
                | MOSTRASTAT: Mostra le tue statistiche di gioco
                | CONDIVIDI: Invia le statistiche della tua ultima partita
                | MOSTRAFEED: Mostra le notifiche degli altri giocatori di WORDLE
                | LOGOUT: Per uscire dal tuo profilo
                """);
        while (status == Status.LOGGED) {
            System.out.printf("> Inserisci un comando: ");
            // Leggo il comando da tastiera.
            String command = scanner.nextLine();
            // Lo invio al server.
            out.println(command);
            // Attendo la risposta dal server.
            String reply = in.readLine();
            if (command.equals("mostrastat")){
                System.out.println(reply);
            }
            // Elaboro la risposta a seconda del comando.
            switch (reply){
                case "exit":
                    System.out.println("[CLIENT] Logging out." + "\n");
                    status = Status.NOTLOGGED;
                    break;
                case "sending":
                    mostraFeeds();
                    break;
                case "sharing":
                    System.out.println("[CLIENT] Condivisione Effettuata!");
                    break;
                case "denied":
                    System.out.println("[CLIENT] Hai già partecipato a questa parola!");
                    break;
                case "playing":
                    status = Status.PLAYING;
                    break;
                case "unable":
                    System.out.println("Non hai niente da condividere! :c");
                    break;
                case "invalid":
                    System.out.println("[CLIENT] Comando non Valido!");
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Gestisce l'interazione con il server per i vari comandi disponibili dopo autentificazione.
     *
     * @throws IOException nel caso in cui vi sia un errore di comunicazione
     */

    public static void game() throws IOException {
        if (status == Status.PLAYING) System.out.println("| Partita Iniziata | Digita logout per uscire! |");
        while (status == Status.PLAYING) {
            System.out.printf("> Inserisci una parola: ");
            // Leggo la parola da tastiera.
            String guess = scanner.nextLine();
            // L'utente effettua il logout. Il tentativo verrà considerato perso.
            if (guess.equals("logout")) {
                out.println("logout");
                status = Status.NOTLOGGED;
                break;
            }
            // Se la lunghezza della parola uguale a 10, invio la parola al server.
            if (guess.length() == 10) {
                out.println(guess);
                // Leggo la risposta.
                String line = in.readLine();
                // Se la stringa è uguale a 10, ma è una parola non valida, avviso l'utente
                if (line.equals("notvalid")) {
                    System.out.println("[SERVER] Parola non Valida!");
                }
                // Se la risposta è "WIN", aggiorno lo stato e scrivo il risultato a schermo.
                else if (line.equals("WIN")){
                    status = Status.LOGGED;
                    System.out.println("[CLIENT] Giocatore ha concluso con una Vittoria! Status Aggiornato." + "\n");
                    break;
                }
                else {
                    String[] parts = line.split(",");
                    System.out.println(line);
                    // Se invece i tentativi sono stati usati tutti e il risultato è "LOSE",
                    // aggiorno lo stato e scrivo il risultato a schermo
                    if (parts[1].equals("Tentativi Rimanenti: 0")) {
                        String stato = in.readLine();
                        if (stato.equals("LOSE")) {
                            status = Status.LOGGED;
                            System.out.println("[CLIENT] Giocatore ha concluso con una Sconfitta! Status Aggiornato." + "\n");
                        }
                    }
                }
            } else {
                // Se la parola scritta dall'utennte ha lunghezza diversa da 10, avviso l'utente.
                System.out.println("[CLIENT] Stringa di lunghezza non valida!");
            }
        }
    }

    /**
     * Si unisce al gruppo multicast una volta completata l'autentificazione.
     *
     * @throws IOException nel caso in cui vi sia un errore di comunicazione
     */
    public static void receiveFeeds() throws IOException {
            multicastSocket = new MulticastSocket(multicastPort);
            // Ottengo l'indirizzo del gruppo e ne controllo la validità.
            InetAddress group = InetAddress.getByName(multicastAddress);
            if (!group.isMulticastAddress()) {
                throw new IllegalArgumentException(
                        "Indirizzo multicast non valido: " + group.getHostAddress());
            }
            // Mi unisco al gruppo multicast.
            multicastSocket.joinGroup(group);
    }

    /**
     * Riceve e mostra a schermo le notifiche condivise dagli altri utenti.
     *
     * @throws IOException nel caso in cui vi sia un errore di comunicazione
     */

    public static void mostraFeeds() throws IOException {
        while(true){
            DatagramPacket packet = new DatagramPacket(new byte[1024], 1024);
            // Ricevo il pacchetto.
            multicastSocket.receive(packet);
            String msg = new String(packet.getData(), packet.getOffset(),
                    packet.getLength());
            System.out.println("[CLIENT]: " + msg);
            if (msg.equals("END")) break;
        }
    }
}