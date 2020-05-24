import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

import javax.imageio.ImageIO;

import edu.mines.jtk.awt.ColorMap;

//"C:/Program Files/ffmpeg/ffmpeg" -r 30 -s 190x135 -i "C:/Users/mille/Documents/VideoTest/file%03d.png" "C:/Users/mille/Documents/out.mpg"
// mandelbrot coloring: http://jussiharkonen.com/gallery/coloring-techniques/
// smooth color algorithm: https://stackoverflow.com/questions/369438/smooth-spectrum-for-mandelbrot-set-rendering    
public class FractalImageGenerator {
	
	// image resolution
	private int width = 1920/4; 
	private int height = 1080/4;
	
	// image center
	private double centerRe = 0;
	private double centerIm = 0;
	private double zoom = 1;
	
	// bounds on real axis
	private double minRe; 
	private double maxRe;
	
	// bounds on imaginary axis
	private double minIm;
	private double maxIm;
	
	// max iterations
	private int maxIterations = 2000;
	
	// continuous iteration count ON/OFF
	private boolean continuousIterationCount = true;
	
	// color of pixels that are in set
	private int inSetColor = Color.BLACK.getRGB();
	
	public static enum ColoringFunction {LINEAR, RECLOG, BLEASDALE, BLEASDALE_INV};
	
	public static enum PaletteFunction {GRAYSCALE, HUE, PRISM, LOAD_FROM_FILE};
	
	public static enum GeneratingFunction {MANDELBROT};
	
	private ColoringFunction coloringFunction = ColoringFunction.BLEASDALE_INV;
	
	private PaletteFunction paletteFunction = PaletteFunction.LOAD_FROM_FILE;
	
	private GeneratingFunction generatingFunction = GeneratingFunction.MANDELBROT;
	
	private ColorMap cmap;
	
	private String PaletteFile = "C:\\Users\\mille\\Documents\\match.png";
	
	/**
	 * constructor, defaults to center = 0 and zoom = 1
	 * **/
	public FractalImageGenerator() {
		recalculateImageBounds();
		System.out.printf("Image bounds initialized with the parameters:\n" + 
				"zoom : %f \nCenter Real : %f \nCenter Imaginary : %f", zoom, centerRe, centerIm);
	}
	
	/**
	 * constructor to allow for parameter passing
	 * **/
	
	public FractalImageGenerator(double centerX, double centerY, double newZoom, int iterations) {
		setZoom(newZoom);
		setCenter(centerX, centerY);
		setMaxIterations(iterations);
		System.out.printf("Image bounds initialized with the parameters:\n" + 
				"zoom : %f \nCenter Real : %f \nCenter Imaginary : %f\n", zoom, centerRe, centerIm);
	}
	
	public void setGeneratorSettings(ColoringFunction color, PaletteFunction palette,  GeneratingFunction gen) {
		coloringFunction = color;
		paletteFunction = palette;
		generatingFunction = gen;
	}
	
	/**
	 * to be called after a zoom or center point change, recalculates image bounds to match the parameters
	 * **/
	private void recalculateImageBounds() {
		double imgHeight = 2 / zoom;
		double imgWidth = imgHeight * (16.0 / 9.0);
		
		double halfRe = imgWidth/2;
		double halfIm = imgHeight/2;
		minRe = centerRe - halfRe;
		maxRe = centerRe + halfRe;
		minIm = centerIm - halfIm;
		maxIm = centerIm + halfIm;
		
	}
	
	/**
	 * not implemented
	 * **/
	public void setResolution(int newWidth, int newHeight) {
		// TODO 	
		width = newWidth;
		height = newHeight;
	}
	
	/**
	 * resets image center and recalculates bounds
	 * **/	
	public void setCenter(double centerX, double centerY) {
		centerRe = centerX;
		centerIm = centerY;
		recalculateImageBounds();
	}
	
	/*
	 * Set the color for pixels inside the set
	 * */
	public void setInSetColor(int color) {
		inSetColor = color;
	}
	
	/*
	 * Turn on/off continuous count mode, makes image render smoother
	 * */
	public void enableContinuousIterationCount(boolean smoothingON) {
		continuousIterationCount = smoothingON;
	}
	
	/**
	 * resets image zoom and recalculates bounds
	 * **/
	public void setZoom(double newZoom) {
		zoom = newZoom;
		recalculateImageBounds();
	}
	
	/**
	 * sets the max iterations for image generation, or bailout value
	 * **/
	public void setMaxIterations(int max) {
		maxIterations = max;
	}
	
	
	public BufferedImage generateImage() {
		return generateImage(generatingFunction, coloringFunction, paletteFunction);
	}
	
	/**
	 * generates an image based of the provided criteria
	 * **/

	public BufferedImage generateImage(GeneratingFunction function, ColoringFunction coloring, PaletteFunction palette) {
		
		if (palette == PaletteFunction.LOAD_FROM_FILE) {
			initCmap(palette);
		}
		
		
		BufferedImage img = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);

		for(int x = 0; x < width; x ++) {
			for (int y = 0; y < height; y++) {
				
				ComplexNumber c = getComplexValueForPixel(x, y);
	
				double count;
				
				if (continuousIterationCount) {
					count = getSmoothedIterationCountForC(c, maxIterations, function);
				}
				else {
					count = getIterationCountForC(c, maxIterations, function);
				}
				
				if (count == -1) {
					img.setRGB(x, y, inSetColor);
				}
				else {
					double index = mapIterationCountToColorIndex(count, maxIterations, coloring);
					int color = mapColorIndexToPaletteFunction(index, palette);
					img.setRGB(x, y, color);	
				}			
			}
		}
		return img;
	}
	
	/**
	 * returns the number of iterations made on a given complex number before the value became unbounded, current implementation of function
	 * assumes mandelbrot set calculation. 
	 * **/
	public BufferedImage testPaletteFunction(PaletteFunction palette) {
		initCmap(palette);
		BufferedImage img = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_RGB);
		
		for (int x = 0; x < width; x ++) {
			double colorIndex = (double) x / width;
			int color = mapColorIndexToPaletteFunction(colorIndex, palette);
			for (int y = 0; y < height; y ++) {
				img.setRGB(x, y, color);	
			}
		}
		return img;
	}
	
	private int getIterationCountForC(ComplexNumber c, int N, GeneratingFunction function) {
		ComplexNumber partialSum = new ComplexNumber(0, 0);
		for (int i = 0; i < N; i++) {
			partialSum = ComplexNumber.add(partialSum.square(), c);
			
			if (partialSum.mod() > 2) {
				return i;
			}
		}
		return -1;
	}
		
	private double getSmoothedIterationCountForC(ComplexNumber c, int N_max, GeneratingFunction function) {
		// http://jussiharkonen.com/gallery/coloring-techniques/
		double M = 2; // bailout value
		int p = 2; // power of term in series for mandelbrot p = 2
		
		ComplexNumber partialSum = new ComplexNumber(0, 0);
		for (int i = 0; i < N_max; i++) {
			partialSum = ComplexNumber.add(partialSum.square(), c);
			double r_N = partialSum.mod();
			if (r_N > M) {
				//u = N + 1 + (1/ln(p)) * ln(ln(M) / ln(r_N)) 
				double u = i + 1 + (Math.log( Math.log(M) / Math.log(r_N) ) / Math.log(p));
				return u;
			}
		}
		return -1;
	}
	
	/**
	 * maps the iteration count to a floating point value between 0 and 1, proportional to the itertion
	 * count
	 * **/
	private double mapIterationCountToLinearColorIndex(double iterationCount, int N) {
		return 1 - (iterationCount * 1f/N);
	}
	
	/**
	 * maps the iteration count to a floating point value between 0 and 1
	 * **/
	private double mapIterationCountToColorIndex(double iterationCount, int N, ColoringFunction coloring){
		// linear index:
		double linear = mapIterationCountToLinearColorIndex(iterationCount, N);
		double weighted;
		
		if (coloring == ColoringFunction.RECLOG){
		    weighted = recLogColoringFunction(linear);
			return weighted;
		}
		
		else if (coloring == ColoringFunction.BLEASDALE_INV){
		    weighted = inversionColoringFunction(bleasdaleColoringFunction(linear));
			return weighted;
		}
		
		else if (coloring == ColoringFunction.BLEASDALE){
		    weighted = bleasdaleColoringFunction(linear);
			return weighted;
		}
		
		else {
			return linear;
		}
		
		
				
	}
	
	/**
	 * maps a floating point value between 0 and 1 to an RGB value
	 * **/
	private int mapColorIndexToPaletteFunction(double index, PaletteFunction palette) {
		//System.out.println(index);
		return cmap.getColor(index).getRGB();
	}	
	
	public void setPaletteFilepath(String path) {
		PaletteFile = path;
	}
	
	private void initCmap(PaletteFunction palette) {
		if (palette == PaletteFunction.PRISM) {
			cmap = new ColorMap(0, 1, ColorMap.PRISM);
		}
		
		else if (palette == PaletteFunction.GRAYSCALE) {
			cmap = new ColorMap(0,1, ColorMap.GRAY);
		}
		
		else if (palette == PaletteFunction.HUE) {
			cmap = new ColorMap(0,1, ColorMap.HUE);
		}
		
		else if (palette == PaletteFunction.LOAD_FROM_FILE) {
			//System.out.println(PaletteFile);
			File imageFile = new File(PaletteFile);
			BufferedImage image = null;
			try {
				image = ImageIO.read(imageFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			byte[] r = new byte[256];
			byte[] g = new byte[256];
			byte[] b = new byte[256];
			
			for (int i = 0; i < 256; i ++) {
				Color color = new Color(image.getRGB(i, 10));
				r[i] = (byte)color.getRed();
				g[i] = (byte)color.getGreen();
				b[i] = (byte)color.getBlue();
			}
			cmap = new ColorMap(0, 1, r, g, b);
		}
	}

	public void readPaletteIntoCmap() {
		
	}
	
	/**
	 * maps the coordinate of a pixel to a point on the complex plane
	 * **/
	private ComplexNumber getComplexValueForPixel(int x, int y) {
		double xM = (maxRe - minRe) / width;
		double xB =  minRe;
		
		double yM = (minIm - maxIm) / height;
		double yB = maxIm;
		
		double a = x*xM + xB;
		double b = y*yM + yB;
		
		ComplexNumber ret = new ComplexNumber(a, b);
		return ret;
	}

	public static double recLogColoringFunction(double index) {
		double a = 1.014009359709570E+00;
	    double b = -1.704643869463363E+01;
	    return 1/(a + b*Math.log(index));
	}
	
	public static double bleasdaleColoringFunction(double index) {
		//name:	Bleasdale-YD
		
		double a =	2.76277171798044E+01;
		double b =	-2.66272707081079E+01;
		double theta =	1.94519115131745E+00;
		double x = index;
		
		
		double inP = a + b*Math.pow(x,theta);
		double ret = x*Math.pow(inP,(-1/theta));
		return ret;
	}
	
	public static double inversionColoringFunction(double index) {
		return 1 - index;
	}

	public void writeImageToFilepath(String filepath) {
		BufferedImage img = generateImage();
		writeImageToFilepath(img, filepath);
	}
	
	public static void writeImageToFilepath(BufferedImage image, String filepath) {
		File f = null;
	     try{
		       f = new File(filepath);
		       ImageIO.write(image, "png", f);
	     }catch(IOException e){
	       System.out.println("Error: " + e);
	     }
	}
	
	public void writeVideoToFilepath(int numFrames, double perFrameFactor, String filepath) {
		for (int i = 0; i < numFrames; i++) {
			double z = Math.pow(perFrameFactor, i);
			System.out.printf("%d : %f\n", i, z);
			setZoom(z);
			BufferedImage image = generateImage();
			writeImageToFilepath(image, filepath + String.format("\\file%03d.png",i));	
			
		}
	}
}