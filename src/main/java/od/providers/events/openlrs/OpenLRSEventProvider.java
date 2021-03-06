/**
 * 
 */
package od.providers.events.openlrs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;


//import org.imsglobal.caliper.events.Event;
import javax.annotation.PostConstruct;

import od.framework.model.Tenant;
import od.providers.BaseProvider;
import od.providers.ProviderData;
import od.providers.ProviderException;
import od.providers.ProviderOptions;
import od.providers.api.PageWrapper;
import od.providers.config.DefaultProviderConfiguration;
import od.providers.config.ProviderConfiguration;
import od.providers.config.ProviderConfigurationOption;
import od.providers.config.TranslatableKeyValueConfigurationOptions;
import od.providers.events.EventProvider;
import od.repository.mongo.MongoTenantRepository;
import od.utils.LRS_Options;
import od.utils.LoggingRequestInterceptor;

import org.apache.commons.lang.StringUtils;
import org.apereo.lai.Event;
import org.apereo.lai.impl.EventImpl;
import org.apereo.openlrs.model.event.v2.EventStats;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author ggilbert
 *
 */
@Component("events_openlrs")
public class OpenLRSEventProvider extends BaseProvider implements EventProvider {

  private static final Logger log = LoggerFactory.getLogger(OpenLRSEventProvider.class);

  private static final String KEY = "events_openlrs";
  private static final String BASE = "OPEN_LRS";
  private static final String NAME = String.format("%s_NAME", BASE);
  private static final String DESC = String.format("%s_DESC", BASE);
  
  @Value("${lrs_options.group_id_prependString}")
  private String prepend;
  
  @Autowired private MongoTenantRepository mongoTenantRepository;
  private ProviderConfiguration providerConfiguration;
  
  @PostConstruct
  public void init() {
    LinkedList<ProviderConfigurationOption> options = new LinkedList<>();
    ProviderConfigurationOption key = new TranslatableKeyValueConfigurationOptions("key", null, ProviderConfigurationOption.TEXT_TYPE, true, "Key", "LABEL_KEY",  true);
    ProviderConfigurationOption secret = new TranslatableKeyValueConfigurationOptions("secret", null, ProviderConfigurationOption.PASSWORD_TYPE, true, "Secret", "LABEL_SECRET", true);
    ProviderConfigurationOption baseUrl = new TranslatableKeyValueConfigurationOptions("base_url", null, ProviderConfigurationOption.URL_TYPE, true, "OpenLRS Base URL", "LABEL_OPENLRS_BASE_URL", false);
    options.add(key);
    options.add(secret);
    options.add(baseUrl);

    providerConfiguration = new DefaultProviderConfiguration(options);
  }

  @Override
  public String getKey() {
    return KEY;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDesc() {
    return DESC;
  }

   @Override
  public ProviderConfiguration getProviderConfiguration() {
    return providerConfiguration;
  }

  /* (non-Javadoc)
   * @see od.providers.events.EventProvider#getEventsForUser(od.providers.ProviderOptions, org.springframework.data.domain.Pageable)
   */
  @Override
  public Page<org.apereo.lai.Event> getEventsForUser(ProviderOptions options, Pageable pageable) throws ProviderException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Page<org.apereo.lai.Event> getEventsForCourse(ProviderOptions options, Pageable pageable) throws ProviderException {
    RestTemplate restTemplate = new RestTemplate();
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON,
        MediaType.valueOf("text/javascript")));
    restTemplate.setMessageConverters(Arrays.<HttpMessageConverter<?>> asList(converter));
    
    Tenant tenant = mongoTenantRepository.findOne(options.getTenantId());
    
    ProviderData providerData = tenant.findByKey(KEY);
    
    HttpEntity headers = new HttpEntity<>(createHeadersWithBasicAuth(providerData.findValueForKey("key"), providerData.findValueForKey("secret")));
    String baseUrl = providerData.findValueForKey("base_url");

    String caliperUrl = buildUrl(baseUrl, "/v1/caliper");
    ParameterizedTypeReference<PageWrapper<org.apereo.openlrs.model.event.v2.Event>> responseType = new ParameterizedTypeReference<PageWrapper<org.apereo.openlrs.model.event.v2.Event>>() {};
    PageWrapper<org.apereo.openlrs.model.event.v2.Event> pageWrapper = restTemplate.exchange(buildUri(caliperUrl, null), HttpMethod.GET, headers, responseType).getBody();
    
    if(pageWrapper!=null) {
    	log.debug(pageWrapper.toString());
    }
    
    List<Event> output;
    if (pageWrapper != null && pageWrapper.getContent() != null && !pageWrapper.getContent().isEmpty()) {
      List<org.apereo.openlrs.model.event.v2.Event> page = pageWrapper.getContent();
      output = new LinkedList<Event>();
      DateTimeFormatter fmt = ISODateTimeFormat.dateTime();
      for (org.apereo.openlrs.model.event.v2.Event v2Event : page) {
        EventImpl event = new EventImpl();
        event.setActor(v2Event.getActor().getId());
        event.setVerb(v2Event.getAction());
        if (v2Event.getObject() != null) {
          event.setObject(v2Event.getObject().getId());
        }
        
        if (v2Event.getEventTime() != null) {
          event.setTimestamp(fmt.print(v2Event.getEventTime()));
        }
        else if (v2Event.getStoredTime() != null) {
          event.setTimestamp(fmt.print(v2Event.getStoredTime()));
        }
        else {
          continue;
        }
        event.setId(v2Event.getId());
        output.add(event);
      }
    }
    else {
      output = new ArrayList<>();
    }
    
    return new PageImpl<Event>(output, pageable, output.size());
  }

  /* (non-Javadoc)
   * @see od.providers.events.EventProvider#getEventsForCourseAndUser(od.providers.ProviderOptions, org.springframework.data.domain.Pageable)
   */
  @Override
  public Page<org.apereo.lai.Event> getEventsForCourseAndUser(ProviderOptions options, Pageable pageable) throws ProviderException {
    // TODO Auto-generated method stub
    return null;
  }


	@Override
	/*
	 * Returns ID of the created event
	 * (non-Javadoc)
	 * @see od.providers.events.EventProvider#postEvent(com.fasterxml.jackson.databind.JsonNode, od.providers.ProviderOptions)
	 */
	public JsonNode postEvent(JsonNode marshallableObject, ProviderOptions options) throws ProviderException {
		RestTemplate restTemplate = new RestTemplate();
		
		//set interceptors/requestFactory
		ClientHttpRequestInterceptor ri = new LoggingRequestInterceptor();
		List<ClientHttpRequestInterceptor> ris = new ArrayList<ClientHttpRequestInterceptor>();
		ris.add(ri);
		restTemplate.setInterceptors(ris);
		restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
		
		
	    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
	    converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON,
	        MediaType.valueOf("text/javascript")));
	    restTemplate.setMessageConverters(Arrays.<HttpMessageConverter<?>> asList(converter));
	    
	    Tenant tenant = mongoTenantRepository.findOne(options.getTenantId());
	    
	    ProviderData providerData = tenant.findByKey(KEY);
	    String baseUrl = providerData.findValueForKey("base_url");
	    String caliperUrl = buildUrl(baseUrl, "/v1/caliper");
	    
	    HttpHeaders headers = createHeadersWithBasicAuth(providerData.findValueForKey("key"), providerData.findValueForKey("secret"));
	    HttpEntity<JsonNode> entity = new HttpEntity<JsonNode>(marshallableObject, headers);
	    
	    ResponseEntity<JsonNode> res = restTemplate.exchange(buildUri(caliperUrl, null), HttpMethod.POST, entity, JsonNode.class);

	    return res.getBody();	
	}

	@Override
	public EventStats getEventStatsForCourse(ProviderOptions options) {
		
		RestTemplate restTemplate = new RestTemplate();

		String fullCourseId = options.getCourseId();
		if (StringUtils.isNotEmpty(prepend)) {
			log.debug("lrs_options group_id_prependString = " + prepend);
			fullCourseId = prepend + fullCourseId;
		} else {
			log.debug("lrs_options group_id_prependString is empty, Sakai users should set this value");
		}
		
		//set interceptors/requestFactory
		ClientHttpRequestInterceptor ri = new LoggingRequestInterceptor();
		List<ClientHttpRequestInterceptor> ris = new ArrayList<ClientHttpRequestInterceptor>();
		ris.add(ri);
		restTemplate.setInterceptors(ris);
		restTemplate.setRequestFactory(new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory()));
		
		
	    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
	    converter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON,
	        MediaType.valueOf("text/javascript")));
	    restTemplate.setMessageConverters(Arrays.<HttpMessageConverter<?>> asList(converter));
	    
	    Tenant tenant = mongoTenantRepository.findOne(options.getTenantId());
	    
	    ProviderData providerData = tenant.findByKey(KEY);
	    String baseUrl = providerData.findValueForKey("base_url");
	    String caliperUrl = buildUrl(baseUrl, "/v1/caliper/stats");
	    
	    HttpHeaders headers = createHeadersWithBasicAuth(providerData.findValueForKey("key"), providerData.findValueForKey("secret"));

	    UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(caliperUrl)
	            .queryParam("groupId", fullCourseId);

	    HttpEntity<?> entity = new HttpEntity<>(headers);

	    HttpEntity<EventStats> response = restTemplate.exchange(
	            builder.build().encode().toUri(), 
	            HttpMethod.GET, 
	            entity, 
	            EventStats.class);

	    return response.getBody();	
	}
}
