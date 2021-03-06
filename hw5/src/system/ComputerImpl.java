package system;

import api.Space;
import api.Task;
import results.SetArg;

import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * ComputerImplementation of the interface given by Computer
 */
public class ComputerImpl extends UnicastRemoteObject implements Computer {

    private final Space space;
    private BlockingQueue<Task> readyTaskQ;
    private Stack<Task> taskSpaceQ;
    private BlockingQueue<Task> waitTaskQ;
    private Stack<UUID> idsOfTasksCompleted;
    private BlockingQueue<SetArg> setArgQ;
    private final int availableProcessors;
    private List<Core> cores;
    private boolean exit = false;
    private boolean multiCore;
    private boolean ameliorateLatency;
    private int computerId;

    /**
     * Constructor of ComputerImpl
     * @param space The space which computer relates to
     * @throws RemoteException
     */
    public ComputerImpl(Space space, boolean ameliorateLatency, boolean multiCore) throws RemoteException {
        this.space = space;
        waitTaskQ = new LinkedBlockingQueue<Task>();
        setArgQ = new LinkedBlockingQueue<SetArg>();
        taskSpaceQ = new Stack<Task>();
        idsOfTasksCompleted = new Stack<UUID>();
        cores = new ArrayList<Core>();
        availableProcessors = Runtime.getRuntime().availableProcessors();
        this.multiCore = multiCore;
        this.ameliorateLatency = ameliorateLatency;
        System.out.println("Number of processors: " + availableProcessors);
        if(ameliorateLatency) {
            readyTaskQ = new LinkedBlockingQueue<Task>();
        } else {
            if(multiCore) {
                readyTaskQ = new LinkedBlockingQueue<Task>(availableProcessors);
            } else {
                readyTaskQ = new LinkedBlockingQueue<Task>(1);
            }
        }
        if(multiCore) {
            int i = 0;
            while(i < availableProcessors) {
                cores.add(startCore(i));
                i++;
            }
        } else {
            cores.add(startCore(0));
        }
        System.out.println("Size :" + cores.size());
    }

    private Core startCore(int i) {
        CoreImpl core = new CoreImpl(this, i);
        Thread t = new Thread(core);
        t.start();
        return core;
    }

    public void run() {
        List<Task> tasks = new ArrayList<Task>();
        List<SetArg> setArgs = new ArrayList<SetArg>();
        List<UUID> taskIds = new ArrayList<UUID>();
        long last = System.currentTimeMillis();
        while(!exit) {
            try {
                // WaitQTasks to space
                if(waitTaskQ.size() > 0) {
                    try {
                        while (waitTaskQ.size() > 0) {
                            tasks.add(waitTaskQ.take());
                        }
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                    space.putAllWaitQ(tasks);
                    tasks.clear();
                }

                // ReadyQTasks to space
                if(taskSpaceQ.size() > 0) {
                    while(taskSpaceQ.size() > 0) {
                        tasks.add(taskSpaceQ.pop());
                    }
                    space.putAllReadyQ(tasks);
                    tasks.clear();
                }

                // SetArgs to space
                if(setArgQ.size() > 0) {
                    try {
                        while (setArgQ.size() > 0) {
                            setArgs.add(setArgQ.take());
                        }
                    } catch(InterruptedException e) {
                        e.printStackTrace();
                    }
                    space.setAllArgs(setArgs);
                    setArgs.clear();
                }

                if(idsOfTasksCompleted.size() > 0) {
                    while(idsOfTasksCompleted.size() > 0) {
                        taskIds.add(idsOfTasksCompleted.pop());
                    }
                    space.reportTaskCompleted(taskIds, computerId);
                    idsOfTasksCompleted.clear();
                }

                if(System.currentTimeMillis() >= (last + 2000)) {
                    int i = 0;
                    for(Core core : cores) {
                        i+=core.getNumberOfTasks();
                        System.out.printf("A core has completed %d tasks.%n", core.getNumberOfTasks());
                    }
                    last = System.currentTimeMillis();
                    System.out.printf("Number of tasks completed %d.%n", i);
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Implementation of compute
     * @param t Task to be executed
     * @throws RemoteException
     */
    @Override
    public boolean compute(Task t) throws RemoteException {
        readyTaskQ.add(t);
        return true;
    }

    private void logQueues() {
        System.out.printf("ReadyQ: %d.%n", readyTaskQ.size());
        System.out.printf("WaitQ: %d.%n", waitTaskQ.size());
        System.out.printf("TaskSpace: %d.%n", taskSpaceQ.size());
    }

    @Override
    /**
     * Exits the computer
     * @throws RemoteException
     */
    public void exit() throws RemoteException {
        System.out.printf("Computer completed %d tasks.%n", 0);
        System.exit(0);
    }

    public void setId(int id) {
        this.computerId = id;
    }


    class CoreImpl implements Core {
        final Computer computer;
        private int numOfTasks = 0;
        private final int id;
        private boolean exit;
        private Task task;

        private CoreImpl(Computer computer, int id) {
            this.computer = computer;
            this.id = id;
            exit = false;
        }

        @Override
        public void run() {
            System.out.printf("Core %d running.%n", id);
            while(!exit) {
                try {
                    task = readyTaskQ.take();
                } catch(InterruptedException e) {

                }
                if(task == null) {
                    continue;
                }
                execute(task);
                numOfTasks++;
                idsOfTasksCompleted.push(task.getTaskId());
            }
        }

        @Override
        public <T> void execute(Task<T> t) {
            t.execute(this);
        }

        @Override
        public <T> void compute(Task<T> t) {
            if(t.isReadyToExecute()) {
                taskSpaceQ.add(t);
            } else {
                waitTaskQ.add(t);
            }
        }

        @Override
        public <T> void setArg(UUID id, T r) {
            setArgQ.add(new SetArg(id, r));
        }

        @Override
        public void exit() {
            exit = true;
        }

        @Override
        public int getNumberOfTasks() {
            return numOfTasks;
        }
    }

    /**
     * Main function to start register and the name service and bind a stub of this computer to SERVICE_NAME given in
     * the computer interface.
     * @param args unused
     * @throws RemoteException
     */
    public static void main(String[] args) throws Exception {

        if(System.getSecurityManager() == null) {
            System.setSecurityManager(new SecurityManager());
        }

        if(args.length < 3) {
            System.exit(-1);
        }
        String domain = args[0];
        boolean multicore = Boolean.valueOf(args[1]);
        boolean ameliorateLatency = Boolean.valueOf(args[2]);

        String url = "rmi://" + domain + ":" + Space.PORT + "/" + Space.SERVICE_NAME;
        final Space space = (Space) Naming.lookup(url);

        ComputerImpl computer = new ComputerImpl(space, ameliorateLatency, multicore);

        int computerId = space.register(computer);

        computer.setId(computerId);

        computer.run();

        System.out.println("Computer initiated.");
    }

}
