package api;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.UUID;

import system.Computer;

/**
 * Space interface.
 * Space is a resource where Client can order Computers to compute for them.
 */
public interface Space extends Remote {

    /**
     * Port where Space is reachable.
     */
    public static int PORT = 2923;


    /**
     * Service name for Space.
     */
    public static String SERVICE_NAME = "Space";

    /**
     * putAll takes a list of tasks and handles the task accordingly
     * @param taskList List of Task
     * @param <T> Value of Task
     * @throws RemoteException
     */
    public <T> void putAll (List<Task<T>> taskList ) throws RemoteException;

    public <T> void put(Task task) throws RemoteException;

    public <T> void putWaitQ(Task<T> t) throws RemoteException;

    public <T> void putReadyQ(Task<T> t) throws RemoteException;

    public <T> void setArg(UUID id, T r) throws RemoteException;

    public Result take() throws RemoteException;

    /**
     * Order space to exit what is currently is doing.
     * @throws RemoteException
     */
    public void exit() throws RemoteException;

    /**
     * Register a computer on the space which is available for computation.
     * @param computer Computer to be added
     * @throws RemoteException
     */
    void register( Computer computer ) throws RemoteException;
}