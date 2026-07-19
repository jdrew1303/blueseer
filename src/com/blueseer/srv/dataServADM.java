/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn 

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.srv;


import com.blueseer.adm.admData;
import static com.blueseer.adm.admData.addCodeMstr;
import static com.blueseer.adm.admData.addCounter;
import static com.blueseer.adm.admData.addCronMstr;
import static com.blueseer.adm.admData.addFTPMstr;
import static com.blueseer.adm.admData.addJaspMstr;
import static com.blueseer.adm.admData.addMenuMstr;
import static com.blueseer.adm.admData.addMenuTree;
import static com.blueseer.adm.admData.addOrUpdateCodeMstr;
import static com.blueseer.adm.admData.addPanelMstr;
import static com.blueseer.adm.admData.addPksMstr;
import static com.blueseer.adm.admData.addPrtMstr;
import static com.blueseer.adm.admData.addSiteMstr;
import static com.blueseer.adm.admData.addUpdateOVCtrl;
import static com.blueseer.adm.admData.addUpdateOVMstr;
import static com.blueseer.adm.admData.addUserMstr;
import static com.blueseer.adm.admData.deleteCodeMstr;
import static com.blueseer.adm.admData.deleteCounter;
import static com.blueseer.adm.admData.deleteCronMstr;
import static com.blueseer.adm.admData.deleteFTPAttrMstr;
import static com.blueseer.adm.admData.deleteFTPMstr;
import static com.blueseer.adm.admData.deleteJaspMstr;
import static com.blueseer.adm.admData.deleteMenuMstr;
import static com.blueseer.adm.admData.deleteMenuTree;
import static com.blueseer.adm.admData.deletePanelMstr;
import static com.blueseer.adm.admData.deletePksMstr;
import static com.blueseer.adm.admData.deletePrtMstr;
import static com.blueseer.adm.admData.deleteSiteMstr;
import static com.blueseer.adm.admData.deleteUserMstr;
import static com.blueseer.adm.admData.getChangeLog;
import static com.blueseer.adm.admData.getCodeMstr;
import static com.blueseer.adm.admData.getCounter;
import static com.blueseer.adm.admData.getCronInit;
import static com.blueseer.adm.admData.getCronMstr;
import static com.blueseer.adm.admData.getFTPAttr;
import static com.blueseer.adm.admData.getFTPAttrHash;
import static com.blueseer.adm.admData.getFTPMstr;
import static com.blueseer.adm.admData.getInitMinimum;
import static com.blueseer.adm.admData.getJaspMstr;
import static com.blueseer.adm.admData.getMenuCount;
import static com.blueseer.adm.admData.getMenuMstr;
import static com.blueseer.adm.admData.getMenuTree;
import static com.blueseer.adm.admData.getOVCtrl;
import static com.blueseer.adm.admData.getOVMstr;
import static com.blueseer.adm.admData.getPKSInit;
import static com.blueseer.adm.admData.getPanelMstr;
import static com.blueseer.adm.admData.getPksMstr;
import static com.blueseer.adm.admData.getPrtMstr;
import static com.blueseer.adm.admData.getSiteInit;
import static com.blueseer.adm.admData.getSiteMstr;
import static com.blueseer.adm.admData.getUserMenuInit;
import static com.blueseer.adm.admData.getUserMstr;
import static com.blueseer.adm.admData.updateCodeMstr;
import static com.blueseer.adm.admData.updateCounter;
import static com.blueseer.adm.admData.updateCronMstr;
import static com.blueseer.adm.admData.updateFTPMstr;
import static com.blueseer.adm.admData.updateJaspMstr;
import static com.blueseer.adm.admData.updateMenuMstr;
import static com.blueseer.adm.admData.updateMenuTree;
import static com.blueseer.adm.admData.updatePanelMstr;
import static com.blueseer.adm.admData.updatePksMstr;
import static com.blueseer.adm.admData.updatePrtMstr;
import static com.blueseer.adm.admData.updateSiteMstr;
import static com.blueseer.adm.admData.updateUserMstr;
import com.blueseer.edi.FTPMaint;
import com.blueseer.utl.BlueSeerUtils;
import static com.blueseer.utl.BlueSeerUtils.ArrayListStringArrayToJson;
import static com.blueseer.utl.BlueSeerUtils.ArrayListStringToJson;
import static com.blueseer.utl.BlueSeerUtils.arrayToJson;
import static com.blueseer.utl.BlueSeerUtils.confirmServerAuthAPI;
import static com.blueseer.utl.BlueSeerUtils.intToJson;
import static com.blueseer.utl.OVData.addMenuToAllUsers;
import static com.blueseer.utl.OVData.addMenuToUser;
import static com.blueseer.utl.OVData.copyUserPerms;
import static com.blueseer.utl.OVData.deleteMenuToAllUsers;
import static com.blueseer.utl.OVData.deleteMenuToUser;
import static com.blueseer.utl.OVData.getMenusOfUsersListArray;
import static com.blueseer.utl.OVData.getUsersOfMenusList;
import static com.blueseer.utl.OVData.getSystemImageDirectory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 *
 * @author terryva
 */
public class dataServADM extends HttpServlet {
 
    
        
@Override
protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
    }

 @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
    response.setContentType("text/plain");
    
    if (! confirmServerAuthAPI(request, authServ.hmuser)) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().println(" br549edipost authorization failed");
        return;
    }
    
    if (request.getHeader("id") == null || request.getHeader("id").isEmpty()) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      response.getWriter().println(HttpServletResponse.SC_BAD_REQUEST + ": missing id " + "\n" + getHeaders(request) );  
      return;
    }
    
    String id = request.getHeader("id");
    
    switch (id) {
        case "getOVMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.ov_mstr x = getOVMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "getOVCtrl" : { 
            admData.ov_ctrl x = getOVCtrl();
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "getAllPKSKeysExceptStore" : 
            response.getWriter().print(ArrayListStringToJson(admData.getAllPKSKeysExceptStore()));
            break;
     
        case "addSiteMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.site_mstr x = objectMapper.readValue(sb.toString(), admData.site_mstr.class);            
            response.getWriter().print(arrayToJson(addSiteMstr(x)));
            break;
          }
           
        case "updateSiteMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.site_mstr x = objectMapper.readValue(sb.toString(), admData.site_mstr.class);            
            response.getWriter().print(arrayToJson(updateSiteMstr(x)));
            break;
          }
        
        case "deleteSiteMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.site_mstr x = objectMapper.readValue(sb.toString(), admData.site_mstr.class);            
            response.getWriter().print(arrayToJson(deleteSiteMstr(x)));
            break;
          }
        
        case "getSiteMstr" : {
            String[] key = new String[]{request.getHeader("param1")};
            admData.site_mstr x = getSiteMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }

        case "getSiteLogoImage" : {
            // Raw bytes, not JSON -- site_mstr.site_logo only ever stores a
            // filename (see SiteMaint), the actual file lives in the
            // configured image directory. Content-type is guessed from the
            // extension so a future SVG logo (site_logo="logo.svg") is
            // already handled, even though today it's always a raster PNG.
            String filename = request.getHeader("param1");
            File imageFile = new File(BlueSeerUtils.cleanDirString(getSystemImageDirectory()) + filename);
            if (filename == null || filename.isEmpty() || ! imageFile.exists()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                break;
            }
            String contentType = filename.toLowerCase().endsWith(".svg") ? "image/svg+xml" : "image/png";
            response.setContentType(contentType);
            response.getOutputStream().write(Files.readAllBytes(imageFile.toPath()));
            break;
          }


        case "addUserMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.user_mstr x = objectMapper.readValue(sb.toString(), admData.user_mstr.class);            
            response.getWriter().print(arrayToJson(addUserMstr(x)));
            break;
          }
         
        case "updateUserMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.user_mstr x = objectMapper.readValue(sb.toString(), admData.user_mstr.class);            
            response.getWriter().print(arrayToJson(updateUserMstr(x)));
            break;
          }
        
        case "deleteUserMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.user_mstr x = objectMapper.readValue(sb.toString(), admData.user_mstr.class);            
            response.getWriter().print(arrayToJson(deleteUserMstr(x)));
            break;
          }
        
        case "getUserMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.user_mstr x = getUserMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        
        case "getInitMinimum" : { 
            response.getWriter().print(ArrayListStringArrayToJson(getInitMinimum(request.getHeader("param1"), request.getHeader("param2"), request.getHeader("param3"))));
            break;  
        } 
        
        case "getSiteInit" : { 
            response.getWriter().print(ArrayListStringArrayToJson(getSiteInit(request.getHeader("param1"), request.getHeader("param2"))));
            break;  
        } 
        
        case "getCronInit" : { 
            response.getWriter().print(ArrayListStringArrayToJson(getCronInit(request.getHeader("param1"), request.getHeader("param2"))));
            break;  
        }    
        
        case "getPKSInit" : { 
            response.getWriter().print(ArrayListStringArrayToJson(getPKSInit(request.getHeader("param1"), request.getHeader("param2"))));
            break;  
        } 
        
        case "getUserMenuInit" : { 
            response.getWriter().print(ArrayListStringArrayToJson(getUserMenuInit(request.getHeader("param1"), request.getHeader("param2"))));
            break;  
        }
        
        case "getMenuCount" : { 
            response.getWriter().print(intToJson(getMenuCount(request.getHeader("param1"))));
            break;  
        }
        
        case "addFTPMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.ftp_mstr x = objectMapper.readValue(sb.toString(), admData.ftp_mstr.class);            
            response.getWriter().print(arrayToJson(addFTPMstr(x)));
            break;
          }
        
        case "updateFTPMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.ftp_mstr x = objectMapper.readValue(sb.toString(), admData.ftp_mstr.class);            
            response.getWriter().print(arrayToJson(updateFTPMstr(x)));
            break;
          }
        
        case "deleteFTPMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.ftp_mstr x = objectMapper.readValue(sb.toString(), admData.ftp_mstr.class);            
            response.getWriter().print(arrayToJson(deleteFTPMstr(x)));
            break;
          }
        
        case "getFTPMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.ftp_mstr x = getFTPMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "getFTPAttr" : { 
            ArrayList<admData.ftp_attr> x = getFTPAttr(new String[]{request.getHeader("param1")}); 
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "getFTPAttrHash" : { 
            HashMap<String, String> x = getFTPAttrHash(new String[]{request.getHeader("param1")}); 
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "addUpdateOVMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.ov_mstr x = objectMapper.readValue(sb.toString(), admData.ov_mstr.class);            
            response.getWriter().print(arrayToJson(addUpdateOVMstr(x)));
            break;
          }
        
        case "addUpdateOVCtrl" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.ov_ctrl x = objectMapper.readValue(sb.toString(), admData.ov_ctrl.class);            
            response.getWriter().print(arrayToJson(addUpdateOVCtrl(x)));
            break;
          }
        
        case "addChangeLog" : {
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            reader.close();
            ObjectMapper om = new ObjectMapper();
            admData.change_log[] sdarray = om.readValue(sb.toString(), admData.change_log[].class);
            ArrayList<admData.change_log> list = new ArrayList<admData.change_log>(Arrays.asList(sdarray)); 
            response.getWriter().print(arrayToJson(admData.addChangeLog(list)));    
            break; 
            }
        
        case "getChangeLog" : {       
        ArrayList<admData.change_log> xd = getChangeLog(new String[]{request.getHeader("param1"), request.getHeader("param2")});
        ObjectMapper omsd = new ObjectMapper(); 
        String rsd = omsd.writeValueAsString(xd);
        response.getWriter().print(rsd);
        break;
        } 
        
        case "addUpdateFTPAttr" : {
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            reader.close();
            ObjectMapper om = new ObjectMapper();
            String[] ca = sb.toString().split("=_=", -1);
            String x = ca[0];
            ArrayList<String[]> list = om.readValue(ca[1], new TypeReference<ArrayList<String[]>>() {});
            response.getWriter().print(arrayToJson(admData.addUpdateFTPAttr(x, list)));    
            break; 
            }
        
        case "deleteFTPAttrMstr" : {
            response.getWriter().print(arrayToJson(deleteFTPAttrMstr(request.getHeader("param1")
                    )));  
            break;
        }
        
        case "runFTPClient" : {
            response.getWriter().print(ArrayListStringArrayToJson(admData.runFTPClient(request.getHeader("param1"))));     
            break;
        }
            
        case "addCronMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.cron_mstr x = objectMapper.readValue(sb.toString(), admData.cron_mstr.class);            
            response.getWriter().print(arrayToJson(addCronMstr(x)));
            break;
          }
           
        case "updateCronMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.cron_mstr x = objectMapper.readValue(sb.toString(), admData.cron_mstr.class);            
            response.getWriter().print(arrayToJson(updateCronMstr(x)));
            break;
          }
        
        case "deleteCronMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.cron_mstr x = objectMapper.readValue(sb.toString(), admData.cron_mstr.class);            
            response.getWriter().print(arrayToJson(deleteCronMstr(x)));
            break;
          }
        
        case "getCronMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.cron_mstr x = getCronMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
            
        case "addMenuMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_mstr x = objectMapper.readValue(sb.toString(), admData.menu_mstr.class);            
            response.getWriter().print(arrayToJson(addMenuMstr(x)));
            break;
          }
           
        case "updateMenuMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_mstr x = objectMapper.readValue(sb.toString(), admData.menu_mstr.class);            
            response.getWriter().print(arrayToJson(updateMenuMstr(x)));
            break;
          }
        
        case "deleteMenuMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_mstr x = objectMapper.readValue(sb.toString(), admData.menu_mstr.class);            
            response.getWriter().print(arrayToJson(deleteMenuMstr(x)));
            break;
          }
        
        case "getMenuMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.menu_mstr x = getMenuMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
           
        case "addMenuTree" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_tree x = objectMapper.readValue(sb.toString(), admData.menu_tree.class);            
            response.getWriter().print(arrayToJson(addMenuTree(x)));
            break;
          }
           
        case "updateMenuTree" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_tree x = objectMapper.readValue(sb.toString(), admData.menu_tree.class);            
            response.getWriter().print(arrayToJson(updateMenuTree(x)));
            break;
          }
        
        case "deleteMenuTree" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.menu_tree x = objectMapper.readValue(sb.toString(), admData.menu_tree.class);            
            response.getWriter().print(arrayToJson(deleteMenuTree(x)));
            break;
          }
        
        case "getMenuTree" : { 
            String[] key = new String[]{request.getHeader("param1"), request.getHeader("param2")}; 
            admData.menu_tree x = getMenuTree(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        case "addPanelMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.panel_mstr x = objectMapper.readValue(sb.toString(), admData.panel_mstr.class);            
            response.getWriter().print(arrayToJson(addPanelMstr(x)));
            break;
          }
           
        case "updatePanelMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.panel_mstr x = objectMapper.readValue(sb.toString(), admData.panel_mstr.class);            
            response.getWriter().print(arrayToJson(updatePanelMstr(x)));
            break;
          }
        
        case "deletePanelMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.panel_mstr x = objectMapper.readValue(sb.toString(), admData.panel_mstr.class);            
            response.getWriter().print(arrayToJson(deletePanelMstr(x)));
            break;
          }
        
        case "getPanelMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.panel_mstr x = getPanelMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        case "addPksMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            String[] ca = sb.toString().split("=_=", -1);
            admData.pks_mstr x = objectMapper.readValue(ca[0], admData.pks_mstr.class);      
            String rtnkey = ca[1];
            response.getWriter().print(arrayToJson(addPksMstr(x, rtnkey)));
            break;
          }
           
        case "updatePksMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.pks_mstr x = objectMapper.readValue(sb.toString(), admData.pks_mstr.class);            
            response.getWriter().print(arrayToJson(updatePksMstr(x)));
            break;
          }
        
        case "deletePksMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.pks_mstr x = objectMapper.readValue(sb.toString(), admData.pks_mstr.class);            
            response.getWriter().print(arrayToJson(deletePksMstr(x)));
            break;
          }
        
        case "getPksMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.pks_mstr x = getPksMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
        
        case "addPrtMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.prt_mstr x = objectMapper.readValue(sb.toString(), admData.prt_mstr.class);            
            response.getWriter().print(arrayToJson(addPrtMstr(x)));
            break;
          }
           
        case "updatePrtMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.prt_mstr x = objectMapper.readValue(sb.toString(), admData.prt_mstr.class);            
            response.getWriter().print(arrayToJson(updatePrtMstr(x)));
            break;
          }
        
        case "deletePrtMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.prt_mstr x = objectMapper.readValue(sb.toString(), admData.prt_mstr.class);            
            response.getWriter().print(arrayToJson(deletePrtMstr(x)));
            break;
          }
        
        case "getPrtMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.prt_mstr x = getPrtMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        case "addCounter" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.counter x = objectMapper.readValue(sb.toString(), admData.counter.class);            
            response.getWriter().print(arrayToJson(addCounter(x)));
            break;
          }
           
        case "updateCounter" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.counter x = objectMapper.readValue(sb.toString(), admData.counter.class);            
            response.getWriter().print(arrayToJson(updateCounter(x)));
            break;
          }
        
        case "deleteCounter" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.counter x = objectMapper.readValue(sb.toString(), admData.counter.class);            
            response.getWriter().print(arrayToJson(deleteCounter(x)));
            break;
          }
        
        case "getCounter" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.counter x = getCounter(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        case "addCodeMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.code_mstr x = objectMapper.readValue(sb.toString(), admData.code_mstr.class);            
            response.getWriter().print(arrayToJson(addCodeMstr(x)));
            break;
          }
           
        case "addOrUpdateCodeMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.code_mstr x = objectMapper.readValue(sb.toString(), admData.code_mstr.class);            
            response.getWriter().print(arrayToJson(addOrUpdateCodeMstr(x)));
            break;
          }
        
        case "updateCodeMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.code_mstr x = objectMapper.readValue(sb.toString(), admData.code_mstr.class);            
            response.getWriter().print(arrayToJson(updateCodeMstr(x)));
            break;
          }
        
        case "deleteCodeMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.code_mstr x = objectMapper.readValue(sb.toString(), admData.code_mstr.class);            
            response.getWriter().print(arrayToJson(deleteCodeMstr(x)));
            break;
          }
        
        case "getCodeMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.code_mstr x = getCodeMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        case "addJaspMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.jasp_mstr x = objectMapper.readValue(sb.toString(), admData.jasp_mstr.class);            
            response.getWriter().print(arrayToJson(addJaspMstr(x)));
            break;
          }
           
        case "updateJaspMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.jasp_mstr x = objectMapper.readValue(sb.toString(), admData.jasp_mstr.class);            
            response.getWriter().print(arrayToJson(updateJaspMstr(x)));
            break;
          }
        
        case "deleteJaspMstr" : { 
            String line;
            StringBuilder sb = new StringBuilder();  
            BufferedReader reader = request.getReader();  // as string
            while ((line = reader.readLine()) != null) {  
            sb.append(line);
            } 
            ObjectMapper objectMapper = new ObjectMapper();
            admData.jasp_mstr x = objectMapper.readValue(sb.toString(), admData.jasp_mstr.class);            
            response.getWriter().print(arrayToJson(deleteJaspMstr(x)));
            break;
          }
        
        case "getJaspMstr" : { 
            String[] key = new String[]{request.getHeader("param1")}; 
            admData.jasp_mstr x = getJaspMstr(key);
            ObjectMapper objectMapper = new ObjectMapper();
            String r = objectMapper.writeValueAsString(x);
            response.getWriter().print(r);
            break;
          }
         
        
        default:
        response.getWriter().print("");
        System.out.println("error no switch case exists in dataServADM for id: " + id);    
            
    }
    
       
       
    } // doPost
     
    
    private String getHeaders(HttpServletRequest request) {
    
    StringBuilder requestHeaders = new StringBuilder();

            Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String hd = headerNames.nextElement();
                requestHeaders.append("Header  " + hd).append("  Value  " + request.getHeader(hd)).append("\n");
            }
    return requestHeaders.toString();
}


}
