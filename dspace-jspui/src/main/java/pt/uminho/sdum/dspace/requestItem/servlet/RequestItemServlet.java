/*
 * RequestItemServlet.java
 *
 * Version: $Revision: 1.0 $
 *
 * Date: $Date: 2005/01/03 17:59:10 $
 *
 * Copyright (c) 2005, University of Minho.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Hewlett-Packard Company nor the name of the
 * Massachusetts Institute of Technology nor the names of their
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */

package pt.uminho.sdum.dspace.requestItem.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.log4j.Logger;

import org.dspace.app.webui.servlet.DSpaceServlet;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;

import org.dspace.core.LogManager;
import org.dspace.eperson.EPerson;
import org.dspace.handle.HandleManager;
import org.dspace.content.Item;
import org.dspace.content.DCValue;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.app.webui.util.UIUtil;
import org.dspace.storage.bitstore.BitstreamStorageManager;
import pt.uminho.sdum.dspace.requestItem.util.ReqEmail;
import pt.uminho.sdum.dspace.requestItem.util.RequestItemManager;

/**
 * Servlet for generate a statistisc report
 *
 * @author  Arnaldo Dantas
 * @version $Revision: 1.0 $
 */
public class RequestItemServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(RequestItemServlet.class);
    
    /** The information get by form step */
    public static final int ENTER_FORM_PAGE = 1;

    /** The link by submmiter email step*/
    public static final int ENTER_TOKEN = 2;
    
    /** The link Aproved genarate letter step*/
    public static final int APROVE_TOKEN = 3;

    /* resume leter for request user*/
    public static final int RESUME_REQUEST = 4;

    /* resume leter for request dspace administrator*/
    public static final int RESUME_FREEACESS = 5;

    protected void doDSGet(Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
                // First get the step
        int step = UIUtil.getIntParameter(request, "step");

        switch (step)
        {
        case ENTER_FORM_PAGE:
            processForm(context, request, response);
            break;

        case ENTER_TOKEN:
            processToken(context, request, response);
            break;

        case APROVE_TOKEN:
            processLetter(context, request, response);
            break;

        case RESUME_REQUEST:
            processAttach(context, request, response);
            break;

        case RESUME_FREEACESS:
            processAdmin(context, request, response);
            break;
            
        default:
            processForm(context, request, response);
        }
        context.complete();
    }

    protected void doDSPost(Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        // Treat as a GET
        doDSGet(context, request, response);
    }
 
    private void processForm (Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {        
        // handle
        String handle = request.getParameter("handle");
        
        String bitstream_id=request.getParameter("bitstream-id");
        
        // Title
        String title = null;
        Item item = null;
        if (handle != null && !handle.equals(""))
        {
            item = (Item) HandleManager.resolveToObject(context, handle);
            if (item != null)
            {   
                DCValue[] titleDC = item.getDC("title", null, Item.ANY);
                if (titleDC != null || titleDC.length > 0) 
                    title = titleDC[0].value; 
            }
        }
        if (title == null)
                title="";
          
        // User email from context
        String authEmail = request.getParameter("email");
        EPerson currentUser = context.getCurrentUser();
        String userName = null;
        
        if (currentUser != null && authEmail!=null)
        {
            authEmail = currentUser.getEmail();
            userName = currentUser.getFullName();
        }
        
        
        if (request.getParameter("submit") != null)
        {
            String reqname = request.getParameter("reqname");
            String coment = request.getParameter("coment");
            if (coment == null || coment.equals(""))
                coment = "";
            boolean allfiles = "true".equals(request.getParameter("allfiles"));
            
            // Check all data is there
            if (authEmail == null || authEmail.equals("") ||
                reqname == null || reqname.equals("")) 
            {
                request.setAttribute("handle",handle);
                request.setAttribute("bitstream-id", bitstream_id);
                request.setAttribute("reqname", reqname);
                request.setAttribute("email", authEmail);
                request.setAttribute("coment", coment);
                request.setAttribute("title", title); 
                request.setAttribute("allfiles", allfiles?"true":null); 
                
                request.setAttribute("requestItem.problem", new Boolean(true));
                JSPManager.showJSP(request, response, "/requestItem/request-form.jsp");
                return;
            }

            // All data is there, send the email
            // get submiter email
            EPerson submiter = item.getSubmitter();
       
            //get email 
            try
            {
                ReqEmail email = RequestItemManager.getEmail("requestItem.author");
                email.setField(email.FIELD_TO,submiter.getEmail());
                email.setField(email.FIELD_FROM,ConfigurationManager.getProperty("mail.from.address"));
                email.setField(email.FIELD_HOST,ConfigurationManager.getProperty("mail.server"));
                email.setField(email.FIELD_SUBJECT, I18nUtil.getMessage("jsp.request.item.request-information.title"));

                //Format message template
                email.addArgument(reqname);    //#   requester name
                email.addArgument(authEmail);    //#   requester email
                email.addArgument(allfiles?"todos os ficheiros em acesso restrito"
                        : Bitstream.find(context,Integer.parseInt(bitstream_id)).getName());    //#   request files
                email.addArgument(HandleManager.getCanonicalForm(handle));    //#   request item handle
                email.addArgument(title);    //#   request item title
                email.addArgument(coment);    //#   comment
                email.addArgument(RequestItemManager.getLinkTokenEmail(context, bitstream_id, item.getID(), authEmail, reqname, allfiles));    //#   token link 
                email.addArgument(submiter.getFullName());    //#   submmiter name
                email.addArgument(submiter.getEmail());    //#   submmiter email
                email.addArgument(ConfigurationManager.getProperty("dspace.name"));
                email.addArgument(ConfigurationManager.getProperty("mail.helpdesk"));
                
                email.formatMessage();
                email.sendMessage();
          
                log.info(LogManager.getHeader(context,
                    "sent_email_requestItem",
                    "submitter_id=" + authEmail
                        + ",bitstream_id="+bitstream_id
                        + ",requestEmail="+authEmail));

                JSPManager.showJSP(request, response,
                    "/requestItem/request-send.jsp");
            }
            catch (MessagingException me)
            {
                log.warn(LogManager.getHeader(context,
                    "error_mailing_requestItem",
                    ""), me);
               JSPManager.showInternalError(request, response);
            }
        }
        else
        {
            // Display sugget form
            log.info(LogManager.getHeader(context,
                "show_requestItem_form",
                "problem=false"));
            request.setAttribute("handle", handle);
            request.setAttribute("bitstream-id", bitstream_id);
            request.setAttribute("email", authEmail);
            request.setAttribute("reqname", userName);
            request.setAttribute("title", title);
            request.setAttribute("allfiles", "true");
            JSPManager.showJSP(request, response, "/requestItem/request-form.jsp"); 
        }
   }


    /* recive token
     * get all request data by token
     *  send email to request user
     */
   private void processToken (Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
       // Token
        String token = request.getParameter("token");
       
        TableRow requestItem = RequestItemManager.getRequestbyToken( context, token);
        // validate
        if (requestItem != null)
        {
            Item item = Item.find(context, requestItem.getIntColumn("item_id"));
            String title = "";
             if (item != null)
            {   
                DCValue[] titleDC = item.getDC("title", null, Item.ANY);
                if (titleDC != null || titleDC.length > 0) 
                    title = titleDC[0].value; 
            }
            request.setAttribute("request-name", requestItem.getStringColumn("request_name"));
            request.setAttribute("handle", item.getHandle());
            request.setAttribute("title", title);
            
            JSPManager.showJSP(request, response,
                    "/requestItem/request-information.jsp");
        }else{
            JSPManager.showInvalidIDError(request, response, token, -1);
        }
        
   }
   
   
    /* recive aprovation and generate a letter
     * get all request data by token
     *  send email to request user
     */
   private void processLetter (Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
       // Token
        String token = request.getParameter("token");
        boolean yes = request.getParameter("submit_yes") != null;
        boolean no = request.getParameter("submit_no") != null;

        //get token, get register, get email template, format email, get message to jsp
       TableRow requestItem = RequestItemManager.getRequestbyToken( context, token);
            ReqEmail email = null;
        if(yes)
            email = RequestItemManager.getEmail("requestItem.aprove", I18nUtil.getMessage("jsp.request.item.request-information.title"));
        else if(no)
            email = RequestItemManager.getEmail("requestItem.reject", I18nUtil.getMessage("jsp.request.item.request-information.title"));

        if(email != null)
        {
            Item item = Item.find(context, requestItem.getIntColumn("item_id"));
            Bitstream bit = Bitstream.find(context,requestItem.getIntColumn("bitstream_id"));

            //Format message template
            //#   requester name
            email.addArgument(requestItem.getStringColumn("request_name"));
            //#   requester email
                email.addArgument(requestItem.getStringColumn("request_email"));
            //#   request files
            email.addArgument(requestItem.getBooleanColumn("allfiles")?"todos os ficheiros em acesso restrito"
                : bit.getName());
            //#   request item handle
            email.addArgument(HandleManager.getCanonicalForm(item.getHandle()));
            //#   request item title
            DCValue[] titleDC = item.getDC("title", null, Item.ANY);
            if (titleDC != null || titleDC.length > 0) 
                email.addArgument(titleDC[0].value); 
            else
                email.addArgument("untitled"); 
            //#   submmiter name
            email.addArgument(item.getSubmitter().getFullName());
            // format message
            email.formatMessage();
            //update requestItem
            requestItem.setColumn("accept_request",yes);
            DatabaseManager.update(context, requestItem);
            // page
            request.setAttribute("response", requestItem.getBooleanColumn("accept_request")?"yes":"no");
            request.setAttribute("subject", email.getSubject());
            request.setAttribute("message", email.getMessage());
            JSPManager.showJSP(request, response,
                    "/requestItem/request-letter.jsp");
        }else{
            JSPManager.showInvalidIDError(request, response, token, -1);
        }
        
   }

   /* recive token
     * get all request data by token
     *  send email to request user
     */
   private void processAttach (Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
       // Token
        String token = request.getParameter("token");
        
        //buttom
        boolean submit_next = (request.getParameter("submit_next") != null);
       
        if (submit_next)
        {
            TableRow requestItem = RequestItemManager.getRequestbyToken( context, token);
            // validate
            if (requestItem != null)
            {
                // Token
                String subject = request.getParameter("subject");
                String message = request.getParameter("message");
                try
                {
                    Item item = Item.find(context, requestItem.getIntColumn("item_id"));
                    ReqEmail email = RequestItemManager.getEmail(subject, message);
                    email.setField(email.FIELD_TO,requestItem.getStringColumn("request_email"));
                    email.setField(email.FIELD_FROM,ConfigurationManager.getProperty("mail.from.address"));
                    email.setField(email.FIELD_HOST,ConfigurationManager.getProperty("mail.server"));
                    email.setField(email.FIELD_SUBJECT, I18nUtil.getMessage("jsp.request.item.request-information.title"));

                    // add attach 
                    if(requestItem.getBooleanColumn("accept_request"))
                    {
                        if (requestItem.getBooleanColumn("allfiles"))
                        {
                            Bundle[] bundles = item.getBundles("ORIGINAL");
                            for (int i = 0; i < bundles.length; i++)
                            {
                                Bitstream[] bitstreams = bundles[i].getBitstreams();
                                for (int k = 0; k < bitstreams.length; k++)
                                {
                                    if (!bitstreams[k].getFormat().isInternal() && RequestItemManager.isRestricted(context, bitstreams[k]))
                                    {
                                        email.addAttachment(BitstreamStorageManager.retrieve(context, bitstreams[k].getID())
                                            , bitstreams[k].getName(), bitstreams[k].getFormat().getMIMEType());
                                    }
                                }
                            }
                        }else{
                            Bitstream bit = Bitstream.find(context,requestItem.getIntColumn("bitstream_id"));
                            email.addAttachment(BitstreamStorageManager.retrieve(context, requestItem.getIntColumn("bitstream_id"))
                                    , bit.getName(), bit.getFormat().getMIMEType());
                        }                
                    }
                    email.sendMessage();

                    requestItem.setColumn("decision_date",new Date());
                    DatabaseManager.update(context, requestItem);

                    log.info(LogManager.getHeader(context,
                        "sent_attach_requestItem",
                        "token=" + token));

                    JSPManager.showJSP(request, response,
                        "/requestItem/request-free-acess.jsp");
                }
                catch (MessagingException me)
                {
                    log.warn(LogManager.getHeader(context,
                        "error_mailing_requestItem",
                        ""), me);
                   JSPManager.showInternalError(request, response);
                }            
            }else
                JSPManager.showInvalidIDError(request, response, null, -1);
        }else{
            processToken(context, request, response);
        }
   }

       /* recive aprovation and generate a letter
     * get all request data by token
     *  send email to request user
     */
   private void processAdmin (Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
       // Token
        String token = request.getParameter("token");
        boolean free = request.getParameter("submit_free") != null;
        String name_author = request.getParameter("name");
        String email_author = request.getParameter("email");
        //get token, get register, get email template, format email, get message to jsp
        TableRow requestItem = RequestItemManager.getRequestbyToken( context, token);
        ReqEmail email = null;

        if(requestItem != null && free )
        {
            try
            {
                Item item = Item.find(context, requestItem.getIntColumn("item_id"));

                ReqEmail adm = RequestItemManager.getEmail("requestItem.admin");
                adm.setField(email.FIELD_TO, ConfigurationManager.getProperty("mail.from.address"));
                adm.setField(email.FIELD_FROM,ConfigurationManager.getProperty("mail.from.address"));
                adm.setField(email.FIELD_HOST,ConfigurationManager.getProperty("mail.server"));
                adm.setField(email.FIELD_SUBJECT, I18nUtil.getMessage("jsp.request.item.request-information.title"));

                //Format message template
                adm.addArgument(Bitstream.find(context,requestItem.getIntColumn("bitstream_id")).getName());    //#   request files
                adm.addArgument(HandleManager.getCanonicalForm(item.getHandle()));    //#   request item handle
                adm.addArgument(requestItem.getStringColumn("token"));    //#   token link
                adm.addArgument(name_author);    //#   Author Name
                adm.addArgument(email_author);    //#   Author Email
                adm.formatMessage();
                adm.sendMessage();

                log.info(LogManager.getHeader(context,
                    "sent_adm_requestItem",
                    "token=" + requestItem.getStringColumn("token")
                    +"item_id=" + item.getID()));
                
                JSPManager.showJSP(request, response,
                        "/requestItem/response-send.jsp");
                }
            catch (MessagingException me)
            {
                log.warn(LogManager.getHeader(context,
                    "error_mailing_requestItem",
                    ""), me);
               JSPManager.showInternalError(request, response);
            }            
           
        }else{
            JSPManager.showInvalidIDError(request, response, token, -1);
        }
        
   }

}