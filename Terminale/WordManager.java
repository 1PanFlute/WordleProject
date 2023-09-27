import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.*;
import java.util.Random;
import java.util.Scanner;

/**
 *	Reti e laboratorio III - A.A. 2022/2023
 *	WORDLE: Un gioco di parole 3.0
 *
 *	Classe che contiene:
 * (1) File contenente la parola scelta del momento e gli eventuali giocatori che hanno già partecipato.
 * (2) File contenente il vocabolario usato dal gioco.
 * (3) Metodo run() utilizzato dallo ScheduledExecutorService all'interno del MainWordleServer per scegliere la parola periodicamente.
 * (4) Metodi aggiuntivi per i controlli su parole valide, utenti partecipanti per dare l'accesso agli utenti con tentativo ancora disponibile.
 *
 * Struttura del File game.json:
 *
 * {parola: "", giocatori:[username, username1, username2,...]}
 */

public class WordManager implements Runnable{
    // File per la parola e i giocatori partecipanti.
    private File hasPartecipated = new File("game.json");
    // File contenente il vocabolario di gioco.
    private File vocabulary = new File("words.txt");
    // Variabile contenente la parola estratta.
    private String wordOfTheDay;

    /**
     * Seleziona randomicamente la parola da indovinare e
     * Cambia la parola all'interno del file game.json,
     * Resettando gli utenti partecipanti.
     *
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void run() {

        try {
            // Sceglie una parola randomica dal vocabolario.
            wordOfTheDay = choose();
            // Cambia la parola del momento.
            changeWord(wordOfTheDay);
            System.out.println("[WORDMANAGER] The WORD has CHANGED!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Seleziona randomicamente la parola da indovinare dal vocabolario di gioco.
     *
     * @throws FileNotFoundException in caso di file non esistente.
     * @return                       la parola selezionata.
     */

    public String choose() throws FileNotFoundException {
        String result = null;
        Random rand = new Random();
        int n = 0;
        for(Scanner scanner = new Scanner(vocabulary); scanner.hasNext(); ) {
            ++n;
            String line = scanner.nextLine();
            if(rand.nextInt(n) == 0)
                result = line;
        }

        return result;
    }

    /**
     * Sovrascrive il file game.json cambiando la parola del momento,
     * Resettando a sua volta l'array contenente i giocatori partecipanti.
     *
     * @param newWord contiene la nuova parola da indovinare
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void changeWord (String newWord) throws IOException {
        JsonWriter writer = new JsonWriter(new FileWriter(hasPartecipated));
        writer.beginObject();
        writer.name("parola").value(newWord);
        writer.name("giocatori");
        writer.beginArray();
        writer.endArray();
        writer.endObject();
        writer.close();
    }

    /**
     * Effettua un controllo sui giocatori partecipanti.
     *
     * @param username contiene l'username su cui effettuiamo il controllo.
     * @return             "1" nel caso in cui il giocatore richiedente ha già partecipato.
     *                     "0" altrimenti.
     * @throws FileNotFoundException in caso di file non esistente.
     */

    public String gamePartecipation(String username) throws FileNotFoundException {
        // Inizia la deserializzazione del file .json.
        JsonElement fileElement = JsonParser.parseReader(new FileReader(hasPartecipated));
        JsonObject wordP = fileElement.getAsJsonObject();
        JsonArray userArray = wordP.get("giocatori").getAsJsonArray();
        // Controlla se l'utente è presente nell'array "giocatori"
        for (JsonElement giocatore: userArray){
            String nome = giocatore.getAsString();
            if(username.equals(nome)) return "1";
        }
        return "0";
    }

    /**
     * Prende la parola da indovinare.
     *
     * @return la parola da indovinare.
     */
    public String getWordOfTheDay(){
        return wordOfTheDay;
    }

    /**
     * Effettua un controllo sulla parola proposta dall'utente.
     * @param word contiene la parola proposta dall'utente.
     * @return     "1" nel caso il vocabolario non contenga la parola dell'utente.
     *             "0" altrimenti.
     * @throws FileNotFoundException in caso di file non esistente.
     */
    public String checkIfExist(String word) throws FileNotFoundException {
        if (new Scanner(new File("words.txt")).useDelimiter("\\Z").next().contains(word)) {
            return "0";
        } else {
            return "1";
        }
    }

    /**
     * Inserisce l'utente tra quelli partecipanti alla parola da indovinare del momento.
     * @param username contiene l'username che dobbiamo inserire tra i partecipanti.
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void setHasPartecipated(String username) throws IOException {
        // Inizia la deserializzazione del file .json.
        JsonElement fileElement = JsonParser.parseReader(new FileReader(hasPartecipated));
        JsonObject wordP = fileElement.getAsJsonObject();
        JsonArray userArray = wordP.get("giocatori").getAsJsonArray();
        // Aggiorno il l'array "giocatori"
        userArray.add(username);
        wordP.add("giocatori", userArray);
        // Scrivo l'oggetto aggiornato.
        FileOutputStream o = new FileOutputStream(hasPartecipated);
        o.write(wordP.toString().getBytes());
        o.close();
        System.out.println("[WORDMANAGER] Lista Partecipanti Aggiornata.");
    }
}
