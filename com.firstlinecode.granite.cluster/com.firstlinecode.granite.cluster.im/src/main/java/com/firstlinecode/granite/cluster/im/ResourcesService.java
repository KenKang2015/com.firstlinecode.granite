package com.firstlinecode.granite.cluster.im;

import java.util.concurrent.locks.Lock;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.springframework.stereotype.Component;

import com.firstlinecode.basalt.protocol.core.JabberId;
import com.firstlinecode.basalt.protocol.core.ProtocolException;
import com.firstlinecode.basalt.protocol.core.stanza.error.InternalServerError;
import com.firstlinecode.basalt.protocol.im.stanza.Presence;
import com.firstlinecode.granite.framework.core.annotations.Dependency;
import com.firstlinecode.granite.framework.core.repository.IInitializable;
import com.firstlinecode.granite.framework.im.IResource;
import com.firstlinecode.granite.framework.im.IResourcesRegister;
import com.firstlinecode.granite.framework.im.IResourcesService;
import com.firstlinecode.granite.framework.im.ResourceRegistrationException;

@Component
public class ResourcesService implements IResourcesService, IResourcesRegister, IInitializable {
	@Dependency("ignite")
	private Ignite ignite;
	
	private ResourcesStorageWrapper resourcesStorageWrapper;
	
	@Override
	public void init() {
		resourcesStorageWrapper = new ResourcesStorageWrapper(ignite);
	}
	
	private class ResourcesStorageWrapper {
		private Ignite ignite;
		private volatile IgniteCache<JabberId, Object[]> resourcesStorage;
		
		public ResourcesStorageWrapper(Ignite ignite) {
			this.ignite = ignite;
		}
		
		public IgniteCache<JabberId, Object[]> getResourcesStorage() {
			if (resourcesStorage != null) {
				return resourcesStorage;
			}
			
			synchronized(ResourcesService.this) {
				if (resourcesStorage != null)
					return resourcesStorage;
				
				resourcesStorage = ignite.cache("resources");
			}
			
			return resourcesStorage;
		}
	}
	
	private IgniteCache<JabberId, Object[]> getResourcesStorage() {
		return resourcesStorageWrapper.getResourcesStorage();
	}
	
	private void checkFullJid(JabberId jid) {
		if (jid.getName() == null || jid.getResource() == null) {
			throw new IllegalArgumentException("The resource registration operation needs a full JID.");
		}
	}
	
	@Override
	public void register(final JabberId jid) throws ResourceRegistrationException  {
		checkFullJid(jid);
		
		new ResourceRegistrationTemplate().lockAndRun(jid.getBareId(), new ResourceRegistrationRunner() {
			@Override
			public void run() {
				Object[] existed = getResourcesStorage().get(jid.getBareId());
				if (existed == null) {
					IResource resource = new Resource(jid);
					getResourcesStorage().put(jid.getBareId(), new IResource[] {resource});
				} else {
					Object[] afterChange = new Object[existed.length + 1];
					for (int i = 0; i < existed.length; i++) {
						afterChange[i] = existed[i];
					}
					afterChange[afterChange.length - 1] = new Resource(jid);
					
					getResourcesStorage().put(jid.getBareId(), afterChange);
				}
			}
		});
	}
	
	private class ResourceRegistrationTemplate {
		public void lockAndRun(JabberId jid, ResourceRegistrationRunner runner) throws ResourceRegistrationException {
			Lock lock = getResourcesStorage().lock(jid);
			
			try {
				lock.lock();
				runner.run();
			} finally {
				lock.unlock();
			}
		}
	}
	
	private interface ResourceRegistrationRunner {
		void run() throws ResourceRegistrationException;
	}

	@Override
	public void unregister(final JabberId jid) throws ResourceRegistrationException  {
		checkFullJid(jid);
		
		new ResourceRegistrationTemplate().lockAndRun(jid.getBareId(), new ResourceRegistrationRunner() {
			@Override
			public void run() throws ResourceRegistrationException {
				Object[] existed = getResourcesStorage().get(jid.getBareId());
				if (existed == null) {
					throw new ResourceRegistrationException(String.format("Can't find resource '%s' in resources storage.", jid));
				} else {
					if (existed.length == 1) {
						if (((IResource)existed[0]).getJid().equals(jid)) {
							getResourcesStorage().remove(jid.getBareId());
							return;
						} else {
							throw new ResourceRegistrationException(String.format("Can't find resource '%s' in resources storage.", jid));						}
					}
					
					Object[] afterChange = new Object[existed.length - 1];
					int copyingIndex = 0;
					for (int i = 0; i < existed.length; i++) {
						if (((IResource)existed[i]).getJid().equals(jid)) {
							continue;
						}
						
						afterChange[copyingIndex++] = existed[i];
					}
					
					getResourcesStorage().put(jid.getBareId(), afterChange);
				}
			}
		});
	}

	@Override
	public void setRosterRequested(final JabberId jid) throws ResourceRegistrationException  {
		checkFullJid(jid);
		
		new ResourceRegistrationTemplate().lockAndRun(jid, new ResourceRegistrationRunner() {
			
			@Override
			public void run() throws ResourceRegistrationException {
				Object[] resources = getResourcesStorage().get(jid.getBareId());
				if (resources == null) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				boolean rosterRequestedSet = false;
				for (int i = 0; i < resources.length; i++) {
					Resource resource = (Resource)resources[i];
					if (resource.getJid().equals(jid)) {
						resource.setRosterRequested(true);
						rosterRequestedSet = true;
					}
				}
				
				if (!rosterRequestedSet) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				getResourcesStorage().put(jid.getBareId(), resources);
			}
		});
	}

	@Override
	public void setBroadcastPresence(final JabberId jid, final Presence presence) throws ResourceRegistrationException  {
		checkFullJid(jid);
		
		new ResourceRegistrationTemplate().lockAndRun(jid, new ResourceRegistrationRunner() {
			
			@Override
			public void run() throws ResourceRegistrationException {
				Object[] resources = getResourcesStorage().get(jid.getBareId());
				if (resources == null) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				boolean broadcastPresenceSet = false;
				for (int i = 0; i < resources.length; i++) {
					Resource resource = (Resource)resources[i];
					if (resource.getJid().equals(jid)) {
						resource.setBroadcastPresence(presence);
						broadcastPresenceSet = true;
					}
				}
				
				if (!broadcastPresenceSet) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				getResourcesStorage().put(jid.getBareId(), resources);
			}
		});
	}

	@Override
	public void setAvailable(final JabberId jid) throws ResourceRegistrationException  {
		checkFullJid(jid);
		
		new ResourceRegistrationTemplate().lockAndRun(jid, new ResourceRegistrationRunner() {
			
			@Override
			public void run() throws ResourceRegistrationException {
				Object[] resources = getResourcesStorage().get(jid.getBareId());
				if (resources == null) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				boolean availableSet = false;
				for (int i = 0; i < resources.length; i++) {
					Resource resource = (Resource)resources[i];
					if (resource.getJid().equals(jid)) {
						resource.setAvailable(true);
						availableSet = true;
					}
				}
				
				if (!availableSet) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", jid)));
				}
				
				getResourcesStorage().put(jid.getBareId(), resources);
			}
		});
	}

	@Override
	public void setDirectedPresence(final JabberId from, final JabberId to, final Presence presence) throws ResourceRegistrationException  {
		checkFullJid(from);
		checkFullJid(to);
		
		new ResourceRegistrationTemplate().lockAndRun(to, new ResourceRegistrationRunner() {
			
			@Override
			public void run() throws ResourceRegistrationException {
				Object[] resources = getResourcesStorage().get(to.getBareId());
				
				if (resources == null) {
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", to)));
				}
				
				boolean directedPresenceSet = false;
				for (int i = 0; i < resources.length; i++) {
					if (((IResource)resources[i]).getJid().equals(to)) {
						Resource resource = (Resource)resources[i];
						resource.setDirectedPresence(from, presence);
						directedPresenceSet = true;
					}
				}
				
				if (!directedPresenceSet)
					throw new ProtocolException(new InternalServerError(String.format("Can't find resource '%s' in resources storage.", to)));
				
				if (directedPresenceSet) {
					getResourcesStorage().put(to.getBareId(), resources);
				}
			}
		});
	}

	@Override
	public IResource[] getResources(JabberId jid) {
		Object[] objects = getResourcesStorage().get(jid.getBareId());
		if (objects == null ||objects.length == 0)
			return new IResource[0];
		
		IResource[] resources = new IResource[objects.length];
		
		for (int i = 0; i < objects.length; i++) {
			resources[i] = (IResource)objects[i];
		}
		
		return resources;
	}

	@Override
	public Resource getResource(JabberId jid) {
		checkFullJid(jid);
		
		IResource[] resources = getResources(jid.getBareId());
		if (resources == null || resources.length == 0)
			return null;
		
		for (IResource resource : resources) {
			if (resource.getJid().equals(jid))
				return (Resource)resource;
		}
		
		return null;
	}

}
