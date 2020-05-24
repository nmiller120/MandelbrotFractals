import java.util.concurrent.TimeUnit;

public class Main {
	public static void main(String[] args) {
		
		double zoom = 1;
		double x =  0.3602;
		double y = -0.6413;

		String filepath = "C:\\Users\\mille\\Documents\\test329.png";
		FractalImageGenerator generator = new FractalImageGenerator(x, y, zoom, 1000);
		generator.setResolution(1920, 1080);
		
		long startTime = System.nanoTime();
		generator.writeImageToFilepath(filepath);
	    long endTime = System.nanoTime();
	    long durationInNano = (endTime - startTime);  //Total execution time in nano seconds
	    System.out.printf("Elapsed Time: %d ms\n" ,TimeUnit.NANOSECONDS.toMillis(durationInNano));

	}	
}
