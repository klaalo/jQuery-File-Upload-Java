package info.sudr.file;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import javax.imageio.ImageIO;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.imgscalr.Scalr;
import org.json.JSONArray;
import org.json.JSONObject;

public class UploadServlet extends HttpServlet {

//    private static final long serialVersionUID = 1L;
    private File fileUploadPath;

    @Override
    public void init(ServletConfig config) {
    	//
		// I fixed the uploadpath with the right realPath
		fileUploadPath = new File(config.getServletContext().getRealPath(config.getInitParameter("upload_path")));
    }
        
    /**
        * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
        * 
        */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        
        if (request.getParameter("getfile") != null 
                && !request.getParameter("getfile").isEmpty()) {
            File file = new File(fileUploadPath,
                    request.getParameter("getfile"));
            if (file.exists()) {
                int bytes = 0;
                ServletOutputStream op = response.getOutputStream();

                response.setContentType(getMimeType(file));
                response.setContentLength((int) file.length());
                response.setHeader( "Content-Disposition", "inline; filename=\"" + file.getName() + "\"" );

                byte[] bbuf = new byte[1024];
                DataInputStream in = new DataInputStream(new FileInputStream(file));

                while ((in != null) && ((bytes = in.read(bbuf)) != -1)) {
                    op.write(bbuf, 0, bytes);
                }

                in.close();
                op.flush();
                op.close();
            }
        } else if (request.getParameter("delfile") != null && !request.getParameter("delfile").isEmpty()) {
            File file = new File(fileUploadPath, request.getParameter("delfile"));
            if (file.exists()) {
                file.delete(); // TODO:check and report success
            } 
        } else if (request.getParameter("getthumb") != null && !request.getParameter("getthumb").isEmpty()) {
            File file = new File(fileUploadPath, request.getParameter("getthumb"));
                if (file.exists()) {
                    String mimetype = getMimeType(file);
                    if (mimetype.endsWith("png") || mimetype.endsWith("jpeg") || mimetype.endsWith("gif")) {
                        BufferedImage im = ImageIO.read(file);
                        if (im != null) {
                            BufferedImage thumb = Scalr.resize(im, 75); 
                            ByteArrayOutputStream os = new ByteArrayOutputStream();
                            if (mimetype.endsWith("png")) {
                                ImageIO.write(thumb, "PNG" , os);
                                response.setContentType("image/png");
                            } else if (mimetype.endsWith("jpeg")) {
                                ImageIO.write(thumb, "jpg" , os);
                                response.setContentType("image/jpeg");
                            } else {
                                ImageIO.write(thumb, "GIF" , os);
                                response.setContentType("image/gif");
                            }
                            ServletOutputStream srvos = response.getOutputStream();
                            response.setContentLength(os.size());
                            response.setHeader( "Content-Disposition", "inline; filename=\"" + file.getName() + "\"" );
                            os.writeTo(srvos);
                            srvos.flush();
                            srvos.close();
                        }
                    }
            } // TODO: check and report success
        } else {
    		//
			// return the list of all files in the upload folder
			PrintWriter writer = response.getWriter();
			response.setContentType("application/json");
			JSONArray json = new JSONArray();

			try {
				//
				// loop all files in the folder
				File[] fileList = fileUploadPath.listFiles();
				if (fileList != null) {
					for (File f : fileList) {
						if (f.isFile()) {
							JSONObject jsono = new JSONObject();
							jsono.put("name", f.getName());
							jsono.put("size", f.length());
							jsono.put("url", "upload?getfile=" + f.getName());
							jsono.put("thumbnail_url", "upload?getthumb=" + f.getName());
							jsono.put("delete_url", "upload?delfile=" + f.getName());
							jsono.put("delete_type", "GET");
							json.put(jsono);

						}
					}
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				writer.write(json.toString());
				writer.close();
			}
        }
    }
    
    /**
        * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
        * 
        */
    @SuppressWarnings("unchecked")
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        if (!ServletFileUpload.isMultipartContent(request)) {
            throw new IllegalArgumentException("Request is not multipart, please 'multipart/form-data' enctype for your form.");
        }

        ServletFileUpload uploadHandler = new ServletFileUpload(new DiskFileItemFactory());
        PrintWriter writer = response.getWriter();
        response.setContentType("application/json");
        JSONArray json = new JSONArray();
        try {
            //
            // this list is for file items
            List<FileItem> items = uploadHandler.parseRequest(request);
            //
            // this map will contains other parameters from form input fields or formData option
            // see documentation : https://github.com/blueimp/jQuery-File-Upload/wiki/How-to-submit-additional-Form-Data
	    Map<String, String> otherParameters = new HashMap<String, String>();
	    //
            for (FileItem item : items) {
                if (!item.isFormField()) {
                        File file = new File(fileUploadPath, item.getName());
                        item.write(file);
                        JSONObject jsono = new JSONObject();
                        jsono.put("name", item.getName());
                        jsono.put("size", item.getSize());
                        jsono.put("url", "upload?getfile=" + item.getName());
                        jsono.put("thumbnail_url", "upload?getthumb=" + item.getName());
                        jsono.put("delete_url", "upload?delfile=" + item.getName());
                        jsono.put("delete_type", "GET");
                        json.put(jsono);
                }
                else {
			//
			// these are the other form fields or elements of formData option
			otherParameters.put(item.getFieldName(), item.getString());

                }
            }
        } catch (FileUploadException e) {
                throw new RuntimeException(e);
        } catch (Exception e) {
                throw new RuntimeException(e);
        } finally {
            writer.write(json.toString());
            writer.close();
        }

    }

    private String getMimeType(File file) {
        String mimetype = "";
        if (file.exists()) {
//            URLConnection uc = new URL("file://" + file.getAbsolutePath()).openConnection();
//            String mimetype = uc.getContentType();
//            MimetypesFIleTypeMap gives PNG as application/octet-stream, but it seems so does URLConnection
//            have to make dirty workaround
            if (getSuffix(file.getName()).equalsIgnoreCase("png")) {
                mimetype = "image/png";
            } else {
                javax.activation.MimetypesFileTypeMap mtMap = new javax.activation.MimetypesFileTypeMap();
                mimetype  = mtMap.getContentType(file);
            }
        }
        System.out.println("mimetype: " + mimetype);
        return mimetype;
    }



    private String getSuffix(String filename) {
        String suffix = "";
        int pos = filename.lastIndexOf('.');
        if (pos > 0 && pos < filename.length() - 1) {
            suffix = filename.substring(pos + 1);
        }
        System.out.println("suffix: " + suffix);
        return suffix;
    }
}