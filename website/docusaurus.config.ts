import {themes as prismThemes} from 'prism-react-renderer';
import type {Config} from '@docusaurus/types';
import type * as Preset from '@docusaurus/preset-classic';

// Runs in Node at build time. Theme/branding (colors, logo, navbar polish) is refined by the
// theme pass; the GitHub Pages deploy fields below (url/baseUrl/organizationName/projectName)
// must stay in step with the repo or the published site 404s.

const config: Config = {
  title: 'StarEnchants',
  tagline: 'Legendary custom enchantments & armor sets for Paper + Folia',
  favicon: 'img/favicon.svg',

  future: {
    v4: true,
  },

  // GitHub Pages: https://owengregson.github.io/StarEnchants/
  url: 'https://owengregson.github.io',
  baseUrl: '/StarEnchants/',
  organizationName: 'owengregson',
  projectName: 'StarEnchants',
  trailingSlash: false,

  // The IA is stable and the build is link-clean, so broken links/anchors fail the build.
  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',

  markdown: {
    hooks: {
      onBrokenMarkdownLinks: 'warn',
    },
  },

  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          routeBasePath: 'docs',
          editUrl: 'https://github.com/owengregson/StarEnchants/tree/main/website/',
        },
        blog: false,
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  themeConfig: {
    image: 'img/social-card.svg',
    metadata: [
      {
        name: 'keywords',
        content:
          'minecraft, paper, folia, custom enchantments, armor sets, plugin, ' +
          'cosmic enchants, spigot, server, enchantments',
      },
      {name: 'theme-color', content: '#b98cff'},
      {name: 'twitter:card', content: 'summary_large_image'},
    ],
    colorMode: {
      respectPrefersColorScheme: true,
    },
    announcementBar: {
      id: 'se_beta',
      content:
        '✦ StarEnchants is in active development — for Paper 1.17.1-26.1.2, Folia, and Minecraft 1.8.x.',
      backgroundColor: '#b98cff',
      textColor: '#1b1430',
      isCloseable: true,
    },
    navbar: {
      title: 'StarEnchants',
      logo: {
        alt: 'StarEnchants',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'docsSidebar',
          position: 'left',
          label: 'Documentation',
        },
        {to: '/creator', label: 'Enchant Creator', position: 'left'},
        {
          href: 'https://github.com/owengregson/StarEnchants',
          position: 'right',
          className: 'header-github-link',
          'aria-label': 'StarEnchants on GitHub',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentation',
          items: [
            {label: 'Introduction', to: '/docs/intro'},
            {label: 'Configuring content', to: '/docs/configuring'},
            {label: 'DSL reference', to: '/docs/reference/effects'},
          ],
        },
        {
          title: 'Tools',
          items: [
            {label: 'Enchant Creator', to: '/creator'},
            {label: 'Cookbook', to: '/docs/cookbook'},
          ],
        },
        {
          title: 'More',
          items: [
            {label: 'Releases', href: 'https://github.com/owengregson/StarEnchants/releases'},
            {label: 'GitHub', href: 'https://github.com/owengregson/StarEnchants'},
          ],
        },
      ],
      copyright: `✦ StarEnchants ${new Date().getFullYear()} — open-source under AGPL-3.0. NOT AN OFFICIAL MINECRAFT PRODUCT. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.oneLight,
      darkTheme: prismThemes.dracula,
      additionalLanguages: ['yaml', 'java', 'bash', 'json'],
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
