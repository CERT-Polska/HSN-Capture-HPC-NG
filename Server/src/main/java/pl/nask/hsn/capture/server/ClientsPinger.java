package pl.nask.hsn.capture.server;

import java.util.*;

public class ClientsPinger extends TimerTask implements Observer {
    private Set<Client> clients;
    private Set<Client> toRemove, toAdd;
    private org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(ClientsPinger.class);

    public ClientsPinger() {
        this.clients = Collections.synchronizedSet(new TreeSet<Client>());
        this.toRemove = Collections.synchronizedSet(new TreeSet<Client>());
        this.toAdd = Collections.synchronizedSet(new TreeSet<Client>());
    }

    public void addClient(Client c) {
        clients.add(c);
    }

    public void removeClient(Client c) {
        clients.remove(c);
    }

    public void update(Observable arg0, Object arg1) {
        Client client = (Client) arg0;
        if (client.getClientState() == CLIENT_STATE.DISCONNECTED) {
            //removeClient(client);
            toRemove.add(client);
        } else if (client.getClientState() == CLIENT_STATE.CONNECTED) {
            //addClient(client);
            toAdd.add(client);
        }
    }

    public void run() {

        clients.addAll(toAdd);
        clients.removeAll(toRemove);
        toAdd.clear();
        toRemove.clear();

        for (Iterator<Client> clientIterator = clients.iterator(); clientIterator.hasNext();) {
            Client client = clientIterator.next();
            if (client.getClientState() != CLIENT_STATE.CONNECTING ||
                    client.getClientState() != CLIENT_STATE.DISCONNECTED) {
                logger.info("Sending <ping/>");
                client.send("<ping/>\n");
            }
        }
    }
}