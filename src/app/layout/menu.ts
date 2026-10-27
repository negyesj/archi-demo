/** Menü: szolgáltatásonként, a manifestekben deklarált jogosultsággal. Csak az engedélyezett szolgáltatás látszik. */
export interface MenuItem {
  path: string;
  label: string;
  icon: string;
  service: string;
  permissions: string[];
}

export interface MenuGroup {
  label: string;
  /** Ikon a bal szélső főmenü-oszlopban (fav-item). */
  icon: string;
  items: MenuItem[];
}

export const MENU: MenuGroup[] = [
  {
    label: 'Iratkezelés',
    icon: 'stacked_email',
    items: [
      { path: 'irat/erkeztetes', label: 'Érkeztetés', icon: 'move_to_inbox', service: 'irat', permissions: ['irat.erkeztetes'] },
      { path: 'irat/feldolgozas', label: 'Bontás, iktatás', icon: 'inventory', service: 'irat', permissions: ['irat.read', 'irat.bontas', 'irat.iktatas'] },
      { path: 'irat/kereso', label: 'Iratkereső', icon: 'manage_search', service: 'irat', permissions: ['irat.report'] },
    ],
  },
  {
    label: 'Törzsadatok',
    icon: 'format_list_bulleted',
    items: [{ path: 'partner', label: 'Partnerek', icon: 'business', service: 'partner', permissions: ['partner.read'] }],
  },
  {
    label: 'Adminisztráció',
    icon: 'dns',
    items: [
      { path: 'iam/users', label: 'Felhasználók', icon: 'person', service: 'iam', permissions: ['iam.read'] },
      { path: 'iam/groups', label: 'Csoportok', icon: 'groups', service: 'iam', permissions: ['iam.read'] },
      { path: 'iam/org-units', label: 'Szervezeti egységek', icon: 'account_tree', service: 'iam', permissions: ['iam.read'] },
      { path: 'iam/permissions', label: 'Jogosultságok', icon: 'admin_panel_settings', service: 'iam', permissions: ['iam.permission.manage'] },
      { path: 'iam/integrations', label: 'Integrációk', icon: 'hub', service: 'iam', permissions: ['integration.manage'] },
      { path: 'audit', label: 'Audit napló', icon: 'history', service: 'audit', permissions: ['audit.read'] },
      { path: 'trail', label: 'Műveleti trail', icon: 'timeline', service: 'audit', permissions: ['trail.read'] },
    ],
  },
];
