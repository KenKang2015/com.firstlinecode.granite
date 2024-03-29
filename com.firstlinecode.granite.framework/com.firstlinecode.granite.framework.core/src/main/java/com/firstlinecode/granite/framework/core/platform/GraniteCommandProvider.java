package com.firstlinecode.granite.framework.core.platform;

import java.util.Stack;

import org.eclipse.osgi.framework.console.CommandInterpreter;
import org.eclipse.osgi.framework.console.CommandProvider;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleException;
import org.osgi.framework.BundleListener;

import com.firstlinecode.granite.framework.core.repository.IComponentInfo;
import com.firstlinecode.granite.framework.core.repository.IComponentQueryer;
import com.firstlinecode.granite.framework.core.repository.IDependencyInfo;

public class GraniteCommandProvider implements CommandProvider {
	private static final String MSG_HELP = "\tgranite - managing and monitoring granite application\r\n";
	
	private static final String MSG_DETAIL_HELP =
			"\tgranite services - List all services.\r\n" +
			"\tgranite service <service_id> - Display details for specified service.\r\n" +
			"\tgranite components - List all components.\r\n" +
			"\tgranite close - Shutdown system and exit.\r\n" +
			"\tgranite help - Display help information.\r\n";
	
	private BundleContext bundleContext;
	private IComponentQueryer componentQueryer;
	
	public GraniteCommandProvider(BundleContext bundleContext, IComponentQueryer componentQueryer) {
		this.bundleContext = bundleContext;
		this.componentQueryer = componentQueryer;
	}

	@Override
	public String getHelp() {
		return MSG_HELP;
	}
	
	public Object _help(CommandInterpreter interpreter) {
		String commandName = interpreter.nextArgument();
		if ("granite".equals(commandName))
			return getDetailHelp();
		
		return false;
	}

	private String getDetailHelp() {
		return MSG_DETAIL_HELP;
	}
	
	public void _granite(CommandInterpreter interpreter) {
		String nextArg = interpreter.nextArgument();
		
		if (nextArg == null || "help".equals(nextArg)) {
			printDetailHelp(interpreter);
		} else if ("services".equals(nextArg)) {
			listComponents(interpreter, componentQueryer.getServices());
		} else if ("service".equals(nextArg)) {
			String serviceId = interpreter.nextArgument();
			
			if (serviceId == null) {
				printDetailHelp(interpreter);
				
				return;
			}
			
			IComponentInfo service = componentQueryer.getService(serviceId);
			
			if (service == null) {
				interpreter.print(String.format("Can't find service %s.\n", serviceId));
				return;
			}
			
			printService(interpreter, service);
		} else if ("components".equals(nextArg)) {
			listComponents(interpreter, componentQueryer.getComponents());
		} else if ("close".equals(nextArg)) {
			close();
		} else {
			printDetailHelp(interpreter);
		}
		
	}

	private void close() {
		Bundle systemBundle = bundleContext.getBundle(0);
		systemBundle.getBundleContext().addBundleListener(new BundleListener() {

			@Override
			public void bundleChanged(BundleEvent event) {
				if (event.getBundle().getBundleId() == 0 && event.getType() == BundleEvent.STOPPED) {
					System.exit(0);
				}
			}
			
		});
		
		try {
			systemBundle.stop();
		} catch (BundleException e) {
			throw new RuntimeException("Can't stop system bundle.", e);
		}
	}

	private void printService(CommandInterpreter interpreter, IComponentInfo service) {
		new ServicePrinter(interpreter, service).print();
	}
	
	private class ServicePrinter {
		private CommandInterpreter interpreter;
		private IComponentInfo service;
		private StringBuilder sb;
		
		public ServicePrinter(CommandInterpreter interpreter, IComponentInfo service) {
			this.interpreter = interpreter;
			this.service = service;
		}
		
		public void print() {
			interpreter.print(getServiceInfo());
		}

		private String getServiceInfo() {
			if (sb != null) {
				return sb.toString();
			}
			
			sb = new StringBuilder();
			
			if (!service.isAvailable()) {
				sb.append("* ");
			}
			sb.append(service.getId());
			sb.append("\r\n");
			
			if (service.getDependencies().length > 0) {
				Stack<Integer> hierarchyContext = new Stack<>();
				hierarchyContext.push(getBindingsCountOfAllDependencies(service));
				
				writeDependencies(service.getId(), service.getDependencies(), hierarchyContext);
				
				hierarchyContext.pop();
			}
			
			return sb.toString();
		}

		private int getBindingsCountOfAllDependencies(IComponentInfo component) {
			int count = 0;
			for (IDependencyInfo dependency : component.getDependencies()) {
				String[] bindings = componentQueryer.getComponentBinding(component.getId() + "$" + dependency.getBareId());
				count += (bindings == null ? 1 : bindings.length);
			}
			
			return count;
		}

		private void writeDependencies(String parentId, IDependencyInfo[] dependencies,
					Stack<Integer> hierarchyContext) {
			
			for (IDependencyInfo dependency : dependencies) {
				String[] bindings = componentQueryer.getComponentBinding(parentId + "$" + dependency.getBareId());
				if (bindings != null && bindings.length > 0) {
					for (String binding : bindings) {
						hierarchyContext.push(hierarchyContext.pop() - 1);
						writeBinding(dependency, binding, hierarchyContext);
					}
				} else {
					hierarchyContext.push(hierarchyContext.pop() - 1);
					writeHierarchyLine(hierarchyContext);
					sb.append('?');
					sb.append(dependency.getBareId());
					sb.append("\r\n");
				}
				
			}
			
		}

		private void writeBinding(IDependencyInfo dependency, String binding, Stack<Integer> hierarchyContext) {
			writeHierarchyLine(hierarchyContext);
			
			IComponentInfo binded = findBindedComponent(dependency, binding);
			if (binded == null) {
				sb.append('!');
			} else if (!binded.isAvailable()) {
				sb.append('*');
			}
			
			sb.append(dependency.getBareId());
			sb.append("->");
			sb.append(binding);
			sb.append("\r\n");
			
			if (binded != null) {
				hierarchyContext.push(getBindingsCountOfAllDependencies(binded));
				writeDependencies(binded.getId(), binded.getDependencies(), hierarchyContext);
				hierarchyContext.pop();
			}
		}

		private IComponentInfo findBindedComponent(IDependencyInfo dependency, String binding) {			
			for (IComponentInfo binded : dependency.getBindedComponents()) {
				if (binded.getId().equals(binding)) {
					return binded;
				}
			}
			
			return null;
		}

		private void writeHierarchyLine(Stack<Integer> hierarchyContext) {
			for (int i = 0; i < hierarchyContext.size() - 1; i++) {
				if (hierarchyContext.get(i) > 0) {
					sb.append('|').append("  ");
				} else {
					sb.append("   ");
				}
			}
			
			if (hierarchyContext.peek() == 0) {
				sb.append('\\');
			} else {
				sb.append('+');
			}
			
			sb.append("- ");
		}
	}

	private void printDetailHelp(CommandInterpreter interpreter) {
		interpreter.print(getDetailHelp());
	}

	private void listComponents(CommandInterpreter interpreter, IComponentInfo[] components) {
		StringBuilder sb = new StringBuilder();
		sb.append("id").append("\t").append("State      ").append("\t").append("Service ID\r\n");
		int i = 0;
		for (IComponentInfo component : components) {
			sb.append(i);
			sb.append("\t");
			sb.append(component.isAvailable() ? "Available  " : "Unavailable");
			sb.append("\t");
			sb.append(component.getId());
			sb.append("\r\n");
			i++;
		}
		
		interpreter.print(sb);
	}

}
