package com.heronix.guardian.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.heronix.guardian.adapter.VendorAdapter;
import com.heronix.guardian.model.enums.VendorType;

/**
 * Configuration for vendor adapters.
 *
 * Registers all available VendorAdapter implementations and provides
 * them as a Map indexed by VendorType for easy lookup.
 *
 * @author Heronix Development Team
 * @version 2.0.0
 */
@Configuration
public class VendorAdapterConfig {

    /**
     * Create a map of vendor adapters indexed by vendor type.
     *
     * @param adapters all available VendorAdapter beans
     * @return map of VendorType -> VendorAdapter
     */
    @Bean
    public Map<VendorType, VendorAdapter> vendorAdapters(List<VendorAdapter> adapters) {
        Map<VendorType, VendorAdapter> adapterMap = new HashMap<>();

        for (VendorAdapter adapter : adapters) {
            adapterMap.put(adapter.getVendorType(), adapter);
        }

        return adapterMap;
    }
}
