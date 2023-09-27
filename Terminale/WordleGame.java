import com.google.gson.JsonObject;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Reti e Laboratorio III - A.A. 2022/2023
 * WORDLE: Un gioco di parole 3.0
 *
 * Il thread WordleGame si occupa di interagire con un utente durante la sua sessione di attività.
 *
 * Durante l'autentificazione, la gameLobby e dentro al gioco, il thread WordleGame:
 * (1) riceve un comando dall'utente;
 * (2) esegue l'azione richiesta;
 * (3) comunica al client l'esito dell'operazione e lo stato corrente del gioco.
 *
 * I messaggi di risposta inviati dal WordleGame sono formati da una singola riga.
 * La classe contiene molti messaggi di "avviso" che sono utilizzati per aiutare il client a orientarsi
 * sui cambi di stato o esiti di operazioni richieste dall'utente.
 *
 * (e.g. Utente ha effettuato login con successo,
 *  Server invia un avviso al client e cambia stato in Status.LOGGED,
 *  client legge la risposta del server e capisce che il login è andato a buon fine,
 *  aggiornando il suo status in Status.LOGGED).
 */
public class WordleGame implements Runnable {
    // Socket e stream per la comunicazione con il client.
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // Struttura dati in cui immagazziniamo le notifiche condivise dagli utenti.
    static ArrayList<DatagramPacket> packets = new ArrayList<>();

    // Stato corrente dell'utente.
    private Status status = Status.NOTLOGGED;
    // Stringa contentente il nome dell'utente collegato.
    private String varUser = "";
    // Classe contenente operazioni per l'aggiornamento del database.
    private Utenti dataStructure;
    // Classe contenente operazioni di controllo sulla parola attuale e i giocatori partecipanti.
    private WordManager wordManager;

    // Socket, porta e gruppo multicast.
    private DatagramSocket datagramSocket;
    private int datagramPort;
    private InetAddress group;
    // Inizializzo variabili dedicate alla costruzione della notifica condivisa dall'utente
    // Variabile contentente lo stato dopo una partita di Wordle.
    private String stato;
    // Variabile contenente i tentativi rimanenti all'utente dopo la partita.
    private int tentativiRimanenti;
    // Costruttore che contiene i tentativi usati dall'utente durante la partita di Wordle.
    private String statoGioco = "";

    public WordleGame(Socket socket, DatagramSocket datagramSocket, int datagramPort, InetAddress group, WordManager wordManager){
        this.socket = socket;
        this.datagramSocket = datagramSocket;
        this.datagramPort = datagramPort;
        this.group = group;
        this.wordManager = wordManager;
    }


    /**
     * Contiene la logica del thread WordleGame.
     */


    public void run() {
        try {
            // Apro gli stream di comunicazione con il client.
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            dataStructure = new Utenti();
            // Entro in un ciclo in cui il giocatore puo' autentificarsi, giocare e condividere i propri risultati.
            while (true) {
                // Ciclo per l'autentificazione utente.
                registerLogin();
                // Ciclo per i comandi disponibili dopo autentificazione.
                gameLobby();
                // Ciclo per la partita di Wordle
                tentativiRimanenti=sendWord();
                if (status == Status.INTERRUPTED) break;
            }
            // Chiudo gli stream e la socket.
            in.close();
            out.close();
            socket.close();
        }
        catch (Exception e) {
            try {
                dataStructure.offlineStatus(varUser);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }


    /**
     * Gestisce l'interazione con il client durante l'autentificazione.
     * Si legge il comando dell'utente e si esegue l'azione corrispondente.
     * Quindi si invia il risultato dell'operazione.
     *
     * Il formato della stringa inviata è: [comando] [username] [password]
     *
     * @throws IOException in caso di errore nella ricezione del messaggio
     */

    public void registerLogin() throws IOException{
        while (status ==Status.NOTLOGGED) {
            // Leggo il comando.
            String line = in.readLine();
            String[] parts = line.split(" ");
            // Se il comando non ha tutti i parametri richiesti, mando avviso.
            if (parts.length != 3) {
                out.println("[SERVER] Input non valido.");
            // Altrimenti, controlla il comando che l'utente vuole eseguire.
            } else {
                if (parts[0].equalsIgnoreCase("register")) {
                    register(parts[1], parts[2]);
                }
                else if (parts[0].equalsIgnoreCase("login")) {
                    login(parts[1], parts[2]);
                    varUser = parts[1];
                }
                else{
                    out.println("[SERVER] Comando non valido.");
                }
            }
        }
    }

    /**
     * Gestisce l'interazione con il client dopo l'autentificazione.
     * Si legge il comando dell'utente e si esegue l'azione corrispondente.
     * Quindi si invia il risultato dell'operazione.
     *
     * @throws IOException in caso di errore nella ricezione del messaggio
     */

    public void gameLobby() throws IOException {
        if (status == Status.LOGGED) System.out.println("[WORDLEGAME] Utente loggato.");
        while (status == Status.LOGGED || status == Status.AFTERGAME) {
            // Leggo il comando.
            String line = in.readLine();
            line = line.toLowerCase();
            // Elaboro la risposta a seconda del servizio che l'utente ha richiesto.
            switch (line) {
                //L'utente vuole iniziare a giocare, avvio il controllo per l'idoneità alla partecipazione.
                case "play":
                    playWORDLE();
                    break;
                // L'utente vuole ricevere le proprie statistiche di gioco.
                case "mostrastat":
                    sendMeStatistics();
                    break;
                // L'utente vuole condividere la sua esperienza di gioco.
                case "condividi":
                    // Se l'utente non ha partecipato ad una partita durante la sessione di gioco,
                    // all'utente verrà negato il comando.
                    if (status == Status.AFTERGAME) {share();}
                    else { out.println("unable"); }
                    break;
                //L'utente vuole ricevere le notifiche di gioco degli altri giocatori.
                case "mostrafeed":
                    showMeSharing();
                    break;
                // L'utente vuole uscire.
                case "logout":
                    logout();
                    break;
                // Comando non riconosciuto.
                default:
                    // Messaggio di avviso al client.
                    out.println("invalid");
                    break;
            }
        }
    }

    /**
     * Esegue un controllo sull'unicità dell'username all'interno del database.
     * Se l'username è già presente, avviso l'utente dell'errore.
     * Altrimenti procedo con la registrazione dell'utente nel database.
     * Infine avviso l'utente dell'esito dell'operazione.
     *
     * codeResult assume valore "1" se username è già presente. "0" se è disponibile.
     *
     * @throws IOException in caso di errore nella ricezione del messaggio
     */

    public void register(String username, String password) throws IOException{
        if (username.equals("") || password.equals("")){
            out.println("[SERVER] Si prega di non inserire campi vuoti.");
        } else{
            String codeResult = dataStructure.checkRegister(username);
            if (codeResult.equals("1")){
                out.println("[SERVER] Username non disponibile.");
            }
            else {
                JsonObject newUtente = dataStructure.createNewInstance(username, password);
                dataStructure.update(newUtente);
                // Messaggio di avviso al client.
                out.println("registered");
            }
        }
    }

    /**
     * Esegue un controllo sull'esistenza del profilo utente all'interno del database.
     * Se il profilo esiste, procedo con la autentificazione,
     * Altrimenti avviso l'utente dell'errore.
     * Infine avviso l'utente dell'esito dell'operazione.
     *
     * codeResult assume valore "1" se il profilo è inesistente, altrimenti assume valore "0" .
     *
     * @throws FileNotFoundException in caso di file non esistente
     */

    public void login(String username, String password) throws IOException {

        //check if utente already registrato
        String codeResult = dataStructure.checkLogin(username, password);
        if (codeResult.equals("1")){ out.println("[SERVER] Utente già connesso!");}
        else if (codeResult.equals("2")){ out.println("[SERVER] Username o Password Incorretti.");}
        else{
            System.out.println("[WORDLEGAME] Autentificazione Utente completata!");
            // Messaggio di avviso al client.
            out.println("logged");
            status = Status.LOGGED;
        }
    }

    /**
     * Eseguo il logout dell'utente dal profilo.
     * Aggiorno lo stato e avviso l'utente.
     */

    public void logout() throws IOException {
        dataStructure.offlineStatus(varUser);
        // Messaggio di avviso al client.
        out.println("exit");
        status=Status.NOTLOGGED;
    }

    /**
     * Esegue un controllo sull'eventuale partecipazione già effettuata dall'utente per la parola corrente.
     * Se l'utente risulta aver già partecipato, impedisco l'accesso al gioco.
     * Altrimenti conferisco idoneità all'utente alla partecipazione al gioco.
     * Infine avviso l'utente dell'esito dell'operazione.
     *
     * check assume valore "1" in caso l'utente abbia già partecipato, altrimenti assume valore "0" .
     *
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void playWORDLE() throws IOException {
        //richiesta di inizio gioco: controllo sulla parola del giorno se l'utente ha già partecipato. si: messaggio di errore, no: accetta la richiesta e inizia il gioco
        String check = wordManager.gamePartecipation(varUser); //ricordati di implementare un nome al metodo sopra
        if (check.equals("1")) {
            // Messaggio di avviso al client.
            out.println("denied");
        }
        else{
            wordManager.setHasPartecipated(varUser);
            // Messaggio di avviso al client.
            out.println("playing");
            status= Status.PLAYING;
        }
    }

    /**
     * Inizia la partita di Wordle.
     * Il giocatore ha a disposizione 12 tentativi per indovinare la parola del momento.
     * Il numero di tentativi diminuisce nel caso in cui la parola proposta dall'utente non sia quella corretta.
     * Il numero di tentativi non diminuisce nel caso in cui il giocatore abbia inserito una parola non valida.
     * Ad ogni tentativo dell'utente viene elaborata e inviata una risposta che avrà:
     * x : se la lettera non esiste all'interno della parola da indovinare.
     * - : se la lettera esiste, ma non è nella posizione corretta.
     * o : se la lettera esiste ed è nella posizione corretta.
     * Se la parola proposta coincide con la parola da indovinare, oppure se i tentativi finiscono:
     * la partita finisce, l'utente viene notificato dell'esito della partita e viene aggiornato lo stato.
     * In caso l'utente termini il tentativo effettuando il comando logout, il tentativo verrà considerato perso.
     * la risposta dell'utente contiene due parametri: [rispostaElaborata] [numeroTentativiRimanenti]
     * non valida = parola di lunghezza diversa da 10/ parola non presente nel vocabolario di gioco.
     * @return tentativi numero di tentativi rimanenti.
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public int sendWord() throws IOException {
        // Inizializzo i tentativi.
        int tentativi = 0;
        while (status == Status.PLAYING) {
            // Resetto il costruttore del tentativo utente.
            statoGioco = "";
            // Resetto i tentativi a 12.
            tentativi = 12;
            // Prendo la parola generata del momento.
            String wordOfTheDay = wordManager.getWordOfTheDay();

            while (tentativi != 0) {
                // Leggo la parola proposta dall'utente.
                String userGuess = in.readLine();
                // L'utente effettua il logout, Il tentativo verrà considerato risultare in una sconfitta.
                if (userGuess.equals("logout")){
                    // Aggiorno le statistiche.
                    dataStructure.updataStats(varUser, "LOSE");
                    // Metto l'utente in offline.
                    dataStructure.offlineStatus(varUser);
                    // Aggiorno lo stato.
                    status = Status.NOTLOGGED;
                    break;
                }
                // Inizializzo la stringa risultato.
                String result = "xxxxxxxxxx";
                // Scompongo nelle singole lettere.
                char[] resultChar = result.toCharArray();
                // Controllo se esiste nel vocabolario.
                String code = wordManager.checkIfExist(userGuess);

                if (code.equals("1")) {
                    // La parola non esiste, Messaggio di avviso al client.
                    out.println("notvalid");
                }
                else {
                    // Parola valida, quindi decremento i tentativi.
                    tentativi--;
                    // Caso Vittoria: aggiorno le statistiche, lo stato e mando la risposta al client. Esco da ciclo.
                    if (userGuess.equals(wordOfTheDay)) {
                        // Aggiorno il costruttore del tentativo utente.
                        statoGioco = statoGioco + "oooooooooo\n";
                        stato = "WIN";
                        status = Status.AFTERGAME;
                        dataStructure.updataStats(varUser, stato);
                        out.println(stato);
                        break;
                    }
                    // Scompongo le parole per il confronto.
                    String[] partsToGuess = wordOfTheDay.split(""); // parola scelta dal server
                    String[] partsOfUserGuess = userGuess.split(""); // parola inviata dal client

                    // Inizializzo una HashMap nel quale costruirò la risposta elaborata da mandare all'utente.

                    HashMap<String, Number> hm = new HashMap<>();

                    for (int j = 0; j < wordOfTheDay.length(); j++) {
                        // Se nell'hashmap non c'è la chiave, la aggiungo
                        if (hm.get(partsToGuess[j]) == null) {
                            hm.put(partsToGuess[j], 1);
                        }
                        // se c'è già la aggiorno
                        else {
                            int x = (int) hm.get(partsToGuess[j]);
                            hm.replace(partsToGuess[j], x + 1);
                        }
                    }
                    // Se la lettera esiste ed è nel posto giusto, aggiorno la stringa risultato,
                    // decremento la chiave corrispondente nell'hashmap.
                    for(int i = partsToGuess.length -1; i >= 0; i--) {
                        if (partsOfUserGuess[i].equals(partsToGuess[i])) {
                            resultChar[i] = 'o';
                            hm.replace(partsToGuess[i], (int) hm.get(partsToGuess[i]) - 1);
                        }
                    }
                    // Se la lettera esiste ed è nel posto sbagliato, aggiorno la stringa risultato,
                    // decremento la chiave corrispondente nell'hashmap.
                    for(int i = partsToGuess.length -1; i >= 0; i--) {
                        if (!(partsOfUserGuess[i].equals(partsToGuess[i])) && wordOfTheDay.contains(partsOfUserGuess[i]) && (int) hm.get(partsOfUserGuess[i]) > 0) {
                            resultChar[i] = '-';
                            hm.replace(partsToGuess[i], (int) hm.get(partsToGuess[i]) - 1);
                        }
                    }
                    // Mando la risposta elaborata al client con annesso i tentativi rimanenti.
                    result = String.valueOf(resultChar);
                    statoGioco = statoGioco + result + "\n";
                    out.println("[WORDLE]: " + result + "," + "Tentativi Rimanenti: " + tentativi);
                }
            }
            if (tentativi!=0) { break; }
            // Nel caso di sconfitta: Aggiorno lo stato, aggiorno le statistiche e mando la risposta al client.
            else{
                stato = "LOSE";
                System.out.println("[WORDLEGAME] Hai Perso!");
                out.println(stato);
                status = Status.AFTERGAME;
                dataStructure.updataStats(varUser, stato);
            }
        }
        return tentativi;
    }

    /**
     * Prende le statistiche dal database.
     * Formatta la risposta e la manda al client.
     * @throws FileNotFoundException in caso di file non esistente.
     */
    public void sendMeStatistics() throws FileNotFoundException {
        String stat = dataStructure.fetchStatistics(varUser);
        String[] partsStat = stat.split(",");
        // Variabili contenenti le partite giocate, vinte, perse e la percentuale di vittoria dell'utente.
        int partiteG = Integer.parseInt(partsStat[0]);
        int partiteV = Integer.parseInt(partsStat[1]);
        int partiteL = Integer.parseInt(partsStat[2]);
        int percV;
        if (partiteG == 0) {percV = 0;}
        else{
            percV = (int) (((float) partiteV/(float) partiteG)*100);
        }
        out.printf("[STATISTICHE] Partite Giocate: %d, Partite Vinte: %d, Partite Perse: %d, Percentuale di Vittorie: %d\n", partiteG, partiteV, partiteL, percV);
    }

    /**
     * Aggiunge il messaggio condiviso dal client alla struttura dati apposita.
     * Manda un avviso di condivisione con successo al client.
     */

    public void share() {
        // Messaggio di notifica che l'utente condivide.
        String message = "Stato partita: " + stato + "." + " Tentativi rimanenti: " + tentativiRimanenti + ".\n" + "Stato del Tentativo: \n" + statoGioco;
        byte[] msg = message.getBytes();
        DatagramPacket packet = new DatagramPacket(msg, msg.length,
                group, datagramPort);
        // Metto il messaggio dentro una struttura dati apposita.
        packets.add(packet);
        // Messaggio di avviso al client.
        out.println("sharing");
    }

    /**
     * Mostra sulla CLI le notifiche inviate dal server riguardo alle partite degli altri utenti.
     * Inizia comunicando al client il messaggio "sending" rendendo il client partecipe
     * sull'inizio della trasmissione.
     * Manda un pacchetto contenente il messaggio "END" per avvisare il client sulla fine della trasmissione.
     */

    public void showMeSharing() throws IOException {
        // Messaggio di avviso al client (Inizio Trasmissione).
        out.println("sending");
        for (DatagramPacket p: packets){
            // Invio notifiche al client.
            datagramSocket.send(p);
        }
        String flag = "END";
        byte[] msg = flag.getBytes();
        DatagramPacket packet = new DatagramPacket(msg, msg.length, group, datagramPort);
        // Invio la notifica per la fine della trasmissione.
        datagramSocket.send(packet);
    }
}
