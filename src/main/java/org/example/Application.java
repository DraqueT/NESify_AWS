package org.example;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.MultipartConfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jetty.server.Server;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

@MultipartConfig(fileSizeThreshold=1024*1024*10, 	// 10 MB 
				maxFileSize=1024*1024*6,      		// 6 MB
				maxRequestSize=1024*1024*100)   	// 100 MB
public class Application extends AbstractHandler
{
    private static final int PAGE_SIZE = 3000;
    private static final String INDEX_HTML = loadIndex();
    private byte[] rawImage;
    private Map<String, String> formFields;

    private static String loadIndex() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Application.class.getResourceAsStream("/index.html")))) {
            final StringBuilder page = new StringBuilder(PAGE_SIZE);
            String line = null;

            while ((line = reader.readLine()) != null) {
                page.append(line);
            }

            return page.toString();
        } catch (final Exception exception) {
            return getStackTrace(exception);
        }
    }

    private static String getStackTrace(final Throwable throwable) {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter, true);
        throwable.printStackTrace(printWriter);

        return stringWriter.getBuffer().toString();
    }

    private static int getPort() {
        return Integer.parseInt(System.getenv().get("PORT"));
    }

    private void handleHttpRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Handle HTTP requests here.
        response.getWriter().println(INDEX_HTML);
    }
    
    private void handleHttpNesify(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    	Nesify nesify = new Nesify();
    	
    	try {
			nesify = new Nesify();
			
			populateUploads(request);
			
			nesify.MASTER_PALETTE_SIZE = Integer.parseInt(formFields.get("master_pal_size"));
			nesify.TILE_PALETTE_SIZE = Integer.parseInt(formFields.get("tile_pal_size"));
			nesify.TILE_SIZE = Integer.parseInt(formFields.get("tile_size"));
			nesify.X_RES = Integer.parseInt(formFields.get("x_res"));
			
			if (formFields.containsKey("href_image") && !formFields.get("href_image").trim().isEmpty()) {
				rawImage = getFromUrl(formFields.get("href_image"));
			}
			
			nesify.go(rawImage);
			
			Pixel[][] finalImage = nesify.getFinalImage();
			
			BufferedImage image = new BufferedImage(finalImage.length, finalImage[0].length, BufferedImage.TYPE_INT_ARGB);
			int alpha = 255;
			
			for (int x = 0; x < finalImage.length; x++) {
				for (int y = 0; y < finalImage[x].length; y++) {
					int p = (alpha<<24) | (finalImage[x][y].r<<16) | (finalImage[x][y].g<<8) | finalImage[x][y].b;
					image.setRGB(x, y, p);
				}
			}
			 
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ImageIO.write(image, "png", baos);
			baos.flush();
			byte[] imageInByte = baos.toByteArray();
			baos.close();
			response.setContentType("image/png");
			response.setContentLength(imageInByte.length);
			ServletOutputStream servletoutputstream = response.getOutputStream();
			servletoutputstream.write(imageInByte);
			servletoutputstream.flush();
			System.out.println("SUCCESS");
		} catch (Exception e) {
			response.setContentType("text/html");
			response.getWriter().println("Error: " + e.getLocalizedMessage());
//			System.out.println("MASTER_PALETTE_SIZE: " + nesify.MASTER_PALETTE_SIZE);
//			System.out.println("TILE_PALETTE_SIZE: " + nesify.TILE_SIZE);
//			System.out.println("TILE_SIZE: " + nesify.TILE_SIZE);
//			System.out.println("X_RES: " + nesify.X_RES);
//			System.out.println("URL: " + formFields.get("href_image"));
		}
		
	}
    
    private void populateUploads(HttpServletRequest request) throws FileUploadException, IOException {
    	ServletFileUpload upload = new ServletFileUpload();
        upload.setFileSizeMax(1024*1024*10);
        upload.setSizeMax(1024*1024*10);
        formFields = new HashMap<>();

        // Parse the request
        FileItemIterator iter = upload.getItemIterator(request);
        while (iter.hasNext()) {
            FileItemStream item = iter.next();
            String name = item.getFieldName();

            if (item.isFormField()) {
            	formFields.put(name, Streams.asString(item.openStream()));
            } else {
            	if (!name.trim().isEmpty()) {
	                System.out.println("File field " + name + " with file name "
	                    + item.getName() + " detected. from " + request.getRemoteAddr());
            	}
                
                // presumes single file uploaded, rewrite to allow for more
                rawImage = IOUtils.toByteArray(item.openStream());
            }
        }
    }
    
    private void handleCronTask(HttpServletRequest request, HttpServletResponse response) throws IOException {
        // Handle WorkerTier tasks here.
        response.getWriter().println("Process Task Here.");
    }

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        response.setContentType("text/html;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);

        String pathInfo = request.getPathInfo();
        
        if (pathInfo.equalsIgnoreCase("/crontask")) {
            handleCronTask(request, response);
        } else if (pathInfo.endsWith("/NesifyServlet")) {
        	handleHttpNesify(request, response);
        } else {
            handleHttpRequest(request, response);
        }
    }
    
    private byte[] getFromUrl(String url) throws IOException {
    	int maxDownload = 1024*1024*6; // 6MB
        URL website = new URL(url);
        
        System.out.println("Pulling from url: " + url);

        byte[] outBytes = new byte[maxDownload];
        InputStream stream = website.openStream();

        IOUtils.read(stream, outBytes, 0, maxDownload);
        int size = stream.read();

        if (size != -1) {
            throw new IOException("File size too big: " + size);
        }
        int lastIndex;
        for (lastIndex = outBytes.length - 1; lastIndex > 0 && outBytes[lastIndex] == 0; lastIndex--);

        byte finalBytes[] = new byte[lastIndex + 1];
        
        for (int i = 0; i <= lastIndex; i++) {
            finalBytes[i] = outBytes[i];
        }
        
        return finalBytes;
    }

    public static void main(String[] args) throws Exception
    {
        Server server = new Server(getPort());
        server.setHandler(new Application());
        server.start();
        server.join();
    }
}
