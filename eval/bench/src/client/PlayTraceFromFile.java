package client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;


import edu.mit.benchmark.QueueLimitException;
import edu.mit.benchmark.ThreadBench;

public class PlayTraceFromFile {
	
	
  private static final class DiskStresserWorker extends ThreadBench.Worker {
    private final DiskStresser terminal;
    private final int type;
    public DiskStresserWorker(DiskStresser terminal,int type) {
      this.terminal = terminal;
      this.type = type;
    }

    @Override
    protected void doWork(boolean measure) {
    
      try {
		long latency = terminal.executeTransaction(type);
		terminal.out3.write(latency + "\n");
		terminal.out3.flush();
	} catch (SQLException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    }
  }
  
  
  
  

  public static ArrayList<DiskStresserWorker> makeDiskStresserWorkers(int size, int numTerminals,int type, BufferedWriter out3) throws IOException, SQLException {
    // HACK: Turn off terminal messages
    jTPCCHeadless.SILENT = true;
    jTPCCConfig.TERMINAL_MESSAGES = false;

    jTPCCHeadless head = new jTPCCHeadless();
    head.createTerminals();

    ArrayList<DiskStresserWorker> workers = new ArrayList<DiskStresserWorker>();
    List<DiskStresser> terminals = DiskStresser.getTerminals(size,numTerminals,out3);
    
    for (DiskStresser terminal : terminals) {
      workers.add(new DiskStresserWorker(terminal,type));
    }
    return workers;
  }

  static final int WARMUP_SECONDS = 30;
  static final int MEASURE_SECONDS = 30;

  public static void main(String[] args) throws IOException, SQLException {
	  
	  
	  
   Properties ini = new Properties();
   ini.load(new FileInputStream(System.getProperty("prop")));
	 
   int initialWarmupTime = Integer.parseInt(ini.getProperty("initialWarmupTimeInSec"));
   int intermediateWarmupTime = Integer.parseInt(ini.getProperty("intermediateWarmupTimeInSec"));
   int measuringTime = Integer.parseInt(ini.getProperty("measuringTime"));
   //int coolDownBeforeThrottledMeasures = Integer.parseInt(ini.getProperty("coolDownBeforeThrottledMeasures"));
   int scaleDownPace = Integer.parseInt(ini.getProperty("scaleDownPace"));
   int maxSpeed = Integer.parseInt(ini.getProperty("maxSpeed"));
   
   String fileName = args[1];//ini.getProperty("logfile");
	 
   FileWriter fstream = new FileWriter(fileName + "_details.csv",true);
   BufferedWriter out = new BufferedWriter(fstream);
   
   FileWriter fstream2 = new FileWriter(fileName + "_aggregated.csv",true);
   BufferedWriter out2 = new BufferedWriter(fstream2);

   FileWriter fstream3 = new FileWriter(fileName + "_latency.csv",true);
   BufferedWriter out3 = new BufferedWriter(fstream3);

	  
    MeasureTargetSystem m = new MeasureTargetSystem(out,out2,new StatisticsCollector(ini),intermediateWarmupTime,measuringTime);
    Thread t = new Thread(m);
    t.start();
    
    
    FileReader fis = new FileReader(args[0]);
    
    BufferedReader bf = new BufferedReader(fis);
    
    String strLine;
    while ((strLine = bf.readLine()) != null)   {
    	StringTokenizer st = new StringTokenizer(strLine);
    
    
    int	size = Integer.parseInt(st.nextToken());
    int terminal = Integer.parseInt(st.nextToken());
    int type = Integer.parseInt(st.nextToken());    
    int speed = Integer.parseInt(st.nextToken());
    
    ArrayList<DiskStresserWorker> workers = makeDiskStresserWorkers(size,terminal,type,out3);
    
    m.setSpeed(speed);
    
    try{
    	ThreadBench.Results r = ThreadBench.runRateLimitedBenchmark(workers, intermediateWarmupTime, measuringTime, speed);
    	System.out.println("Rate limited "+ speed +" reqs/s: " + r);
	
    } catch (QueueLimitException e) {
		System.out.println("Can't keep up at " +speed+ "reqs/s... terminating this run and moving on to next workload size");
		break;
	}
	    
	    for(DiskStresserWorker te : workers)
	    	te.terminal.close();
    }
  	
    System.exit(0);
        
  }
}
