package com.uci.inbound.netcore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.uci.adapter.cdn.FileCdnFactory;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreMessageFormat;
import com.uci.adapter.netcore.whatsapp.NetcoreWhatsappAdapter;
import com.uci.adapter.cdn.service.SunbirdCloudMediaService;
import com.uci.adapter.netcore.whatsapp.inbound.NetcoreWhatsAppMessage;
import com.uci.adapter.netcore.whatsapp.inbound.TextType;
import com.uci.adapter.utils.MediaSizeLimit;
import com.uci.inbound.utils.XMsgProcessingUtil;
import com.uci.dao.repository.XMessageRepository;
import com.uci.utils.kafka.SimpleProducer;
import lombok.extern.slf4j.Slf4j;
import com.uci.utils.BotService;
import com.uci.utils.cache.service.RedisCacheService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.xml.bind.JAXBException;

@Slf4j
@RestController
@RequestMapping(value = "/netcore")
public class NetcoreWhatsappConverter {

    @Value("${inboundProcessed}")
    private String inboundProcessed;

    @Value("${gupshup-opted-out}")
    private String optedOut;

    @Value("${inbound-error}")
    private String inboundError;

    private NetcoreWhatsappAdapter netcoreWhatsappAdapter;

    @Autowired
    public SimpleProducer kafkaProducer;

    @Autowired
    public XMessageRepository xmsgRepo;

    @Autowired
    public BotService botService;
    
    @Autowired
    public RedisCacheService redisCacheService;
    
    @Value("${outbound}")
    public String outboundTopic;

    @Value("${messageReport}")
    public String topicReport;

    @Autowired
    public FileCdnFactory fileCdnFactory;

    @Autowired
    public MediaSizeLimit mediaSizeLimit;

    @RequestMapping(value = "/whatsApp", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    public void netcoreWhatsApp(@RequestBody NetcoreMessageFormat message) throws JsonProcessingException, JAXBException {
        System.out.println(message.toString());

        netcoreWhatsappAdapter = NetcoreWhatsappAdapter.builder()
                .botservice(botService)
                .fileCdnProvider(fileCdnFactory.getFileCdnProvider())
                .mediaSizeLimit(mediaSizeLimit)
                .build();
        try {
            NetcoreWhatsAppMessage netcoreWhatsAppMessage = message.getMessages()[0];
            TextType textType = netcoreWhatsAppMessage.getText();
            if(textType!=null && textType.getText()!=null && textType.getText().trim().equalsIgnoreCase("hi")){
                textType.setText("Hi");
                netcoreWhatsAppMessage.setText(textType);
                log.info("Openforge #135229 Handle all possible cases only for Hi, HI, hI, hi "+netcoreWhatsAppMessage);
            }
            XMsgProcessingUtil.builder()
                    .adapter(netcoreWhatsappAdapter)
                    .xMsgRepo(xmsgRepo)
                    .inboundMessage(netcoreWhatsAppMessage)
                    .topicFailure(inboundError)
                    .topicSuccess(inboundProcessed)
                    .kafkaProducer(kafkaProducer)
                    .botService(botService)
                    .redisCacheService(redisCacheService)
                    .topicOutbound(outboundTopic)
                    .topicReport(topicReport)
                    .build()
                    .process();
        } catch(NullPointerException ex) {
            log.error("An error occored : "+ex.getMessage());
        }

    }
}
