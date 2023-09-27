import com.google.gson.*;

import java.io.*;

/**
 *	Reti e Laboratorio III - A.A. 2022/2023
 *	WORDLE: Un gioco di parole 3.0
 *
 *
 * Classe che contiene il file .json avente il database dove eseguo le mie operazioni sul database utenti.
 * Contiene metodi per l'inserimento, aggiornamento, controllo e fetching di dati del database.
 * Le operazioni operano deserializzando il file .json e aggiungendo i dati dove necessario, per poi reiniserire il file aggiornato.
 *
 * Struttura del mio file database.json:
 * [{Utente}, {Utente}, {Utente}...]
 *
 * Utente = {username, password, partite giocate, partite vinte, partite perse}
 *
 */

public class Utenti {
    // File .json che contiene il database utenti.
    private static File input = new File("database.json");

    /**
     * Metodo che aggiorna l'array di utenti all'interno del file database.json.
     *
     * @param obj contiene l'istanza del nuovo utente che richiede la registrazione.
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void update(JsonObject obj) throws IOException {
        // Inizio la deserializzazione del mio database
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray arrayObject = fileElement.getAsJsonArray();
        // Aggiungo il nuovo utente al mio database.
        arrayObject.add(obj);
        System.out.println("[DATABASE] Nuove credenziali Utente ricevute, Aggiornamento Database in corso...");
        // Inserisco il mio array di utenti aggiornato.
        FileOutputStream o = new FileOutputStream(input);
        o.write(arrayObject.toString().getBytes());
        System.out.println("[DATABASE] Aggiornato con successo!");
        o.close();
    }

    /**
     * Metodo che crea una nuova istanza del giocatore che richiede la registrazione.
     *
     * @param firstField contiene l'username da registrare
     * @param secondField contiene la password associata all'username.
     *
     * @return            l'oggetto da inserire nel database utenti.
     */

    public JsonObject createNewInstance(String firstField, String secondField) {
        JsonObject newUtente = new JsonObject();
        newUtente.addProperty("username", firstField);
        newUtente.addProperty("password", secondField);
        newUtente.addProperty("stato", "offline");
        newUtente.addProperty("Partite_Totali",0);
        newUtente.addProperty("Partite_Vinte",0);
        newUtente.addProperty("Partite_Perse",0);
        return newUtente;
    }

    /**
     * Metodo che aggiorna le statistiche del giocatore dopo una partita di Wordle.
     *
     * Funzionamento:
     * (1) Deserializzo il file,
     * (2) Cerco l'oggetto appartenente all'utente richiedente,
     * (3) Prendo le statistiche attuali,
     * (4) Aggiorno i valori a secondo dello stato della partita,
     * (5) Aggiorno l'oggetto con le nuove statistiche,
     * (6) Infine, aggiorno l'array di utenti.
     *
     * @param username contiene l'username del giocatore.
     * @param stato contiene il risultato della partita.
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public void updataStats (String username, String stato) throws IOException {
        // Inizializzo le variabili per l'aggiornamento delle statistiche.
        int tot, vinte, perse;
        Gson gson = new GsonBuilder().create();
        // Inizio la deserializzazione del file .json
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray listaUtenti = fileElement.getAsJsonArray();
        for (JsonElement utente : listaUtenti) {
            JsonObject obj = utente.getAsJsonObject();
            String nomeUtente = obj.get("username").getAsString();
            if (nomeUtente.equals(username)){
                // Prendo i valori al momento presenti nel database.
                tot = obj.get("Partite_Totali").getAsInt();
                vinte = obj.get("Partite_Vinte").getAsInt();
                perse =obj.get("Partite_Perse").getAsInt();
                // In caso di vittoria aggiorno le partite giocate totali e le partite vinte.
                if(stato.equals ("WIN")){
                    tot++;
                    vinte++;
                    JsonElement value = gson.toJsonTree(tot);
                    JsonElement value1 = gson.toJsonTree(vinte);
                    JsonElement value2 = gson.toJsonTree(perse);
                    obj.add("Partite_Totali", value);
                    obj.add("Partite_Vinte", value1);
                    obj.add("Partite_Perse", value2);
                }
                // In caso di sconfitta aggiorno le partite giocate totali e le partite perse.
                else if (stato.equals("LOSE")){
                    tot++;
                    perse++;
                    JsonElement value = gson.toJsonTree(tot);
                    JsonElement value1 = gson.toJsonTree(vinte);
                    JsonElement value2 = gson.toJsonTree(perse);
                    obj.add("Partite_Totali", value);
                    obj.add("Partite_Vinte", value1);
                    obj.add("Partite_Perse", value2);
                }
                // Infine scrivo l'array aggiornato.
                FileOutputStream out = new FileOutputStream(input);
                out.write(listaUtenti.toString().getBytes());
                out.close();
                System.out.println("[DATABASE] Statistiche Aggiornate!");
            }
        }
    }

    /**
     * Metodo che controlla la unicità dell'username che l'utente vuole registrare.
     *
     * @param newUsername contiene l'username del giocatore.
     * @return            "1" se l'username è già presente all'interno del database,
     *                    "0" se l'username è disponibile.
     * @throws FileNotFoundException in caso di file non esistente.
     */

    public String checkRegister(String newUsername) throws FileNotFoundException {
        // Inizio la deserializzazione del file .json.
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray listaUtenti = fileElement.getAsJsonArray();
        for (JsonElement utente : listaUtenti) {
            JsonObject obj = utente.getAsJsonObject();
            String nomeUtente = obj.get("username").getAsString();
            // Se l'utente esiste all'interno dell'array di utenti registrati allora ritorna il codice "1", altrimenti "0".
            if (nomeUtente.equals(newUsername)) return "1";
        }
        return "0";
    }

    /**
     * Metodo che controlla l'esistenza del profilo con cui l'utente tenta di autentificarsi.
     *
     * @param idUsername contiene l'username del giocatore.
     * @param idPassword contiene la password associata all'username
     * @return            "1" se il profilo non esiste,
     *                    "0" altrimenti.
     * @throws IOException in caso di errore nella ricezione del messaggio.
     */

    public String checkLogin(String idUsername, String idPassword) throws IOException {
        // Inizio la deserializzazione del file .json.
        Gson gson = new GsonBuilder().create();
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray listaUtenti = fileElement.getAsJsonArray();
        for (JsonElement utente : listaUtenti) {
            JsonObject obj = utente.getAsJsonObject();
            String nomeUtente = obj.get("username").getAsString();
            String passUtente = obj.get("password").getAsString();
            String stato = obj.get("stato").getAsString();
            // Se esiste il profilo utente specificato ritorna "0", altrimenti "1"
            if (nomeUtente.equals(idUsername) && passUtente.equals(idPassword)){
                if (stato.equals("offline")) {
                    JsonElement value = gson.toJsonTree("online");
                    obj.add("stato", value);
                    FileOutputStream o = new FileOutputStream(input);
                    o.write(listaUtenti.toString().getBytes());
                    o.close();
                    return "0";
                }
                return "1";
            }
        }
        return "2";
    }

    public void offlineStatus(String username) throws IOException {
        Gson gson = new GsonBuilder().create();
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray listaUtenti = fileElement.getAsJsonArray();
        for (JsonElement utente : listaUtenti) {
            JsonObject obj = utente.getAsJsonObject();
            String nomeUtente = obj.get("username").getAsString();
            if (nomeUtente.equals(username)){
                JsonElement value = gson.toJsonTree("offline");
                obj.add("stato", value);
                FileOutputStream o = new FileOutputStream(input);
                o.write(listaUtenti.toString().getBytes());
                o.close();
            }
        }

    }

    /**
     * Metodo che prende dal database le statistiche dell'utente.
     *
     * @param username contiene l'username del giocatore.
     * @return         la stringa contenente le statistiche del giocatore.
     * @throws FileNotFoundException in caso di file non esistente.
     */

    public String fetchStatistics(String username) throws FileNotFoundException {
        // Inizializzo la stringa.
        String stat = "";
        // Inizio la deserializzazione del file .json.
        JsonElement fileElement = JsonParser.parseReader(new FileReader(input));
        JsonArray listaUtenti = fileElement.getAsJsonArray();
        for (JsonElement utente : listaUtenti) {
            JsonObject obj = utente.getAsJsonObject();
            String nomeUtente = obj.get("username").getAsString();
            // Prendo le statistiche dell'utente.
            if (nomeUtente.equals(username)) {
                String numPartite = obj.get("Partite_Totali").getAsString();
                String numVinte = obj.get("Partite_Vinte").getAsString();
                String numPerse = obj.get("Partite_Perse").getAsString();
                stat = numPartite + "," + numVinte + "," + numPerse;
            }
        }
        return stat;
    }
}
