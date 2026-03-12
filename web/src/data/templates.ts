import type { SimulationInput } from '@/api/client'
import i18n from '@/i18n'

export interface Template {
  key: string
  getInput: () => SimulationInput
}

function t(key: string): string {
  return i18n.t(key)
}

export const templates: Template[] = [
  {
    key: 'beauty',
    getInput: () => ({
      product: {
        brandName: t('create.templates.beauty.brandName'),
        name: t('create.templates.beauty.productName'),
        price: 299,
        category: t('create.product.categoryPlaceholder').replace(/^(如：|e\.g\.\s*)/, '') || 'Skincare',
        sellingPoints: t('create.templates.beauty.sellingPoints'),
        productStage: 'NEW_LAUNCH',
        description: t('create.templates.beauty.description'),
      },
      adPlacements: [
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING', 'BRAND_AWARENESS'],
          format: 'VIDEO',
          budget: 200000,
          creativeDescription: t('create.templates.beauty.creative1'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION'],
          format: 'IMAGE_TEXT',
          budget: 200000,
          creativeDescription: t('create.templates.beauty.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'SEARCH',
          objectives: ['CONVERSION'],
          format: 'IMAGE_TEXT',
          budget: 100000,
          creativeDescription: t('create.templates.beauty.creative3'),
        },
      ],
      totalBudget: 500000,
      targetAudience: {
        ageRange: [25, 35],
        gender: 'female',
        region: t('create.templates.beauty.region'),
        interests: [],
      },
    }),
  },
  {
    key: 'ecommerce',
    getInput: () => ({
      product: {
        brandName: t('create.templates.ecommerce.brandName'),
        name: t('create.templates.ecommerce.productName'),
        price: 89,
        category: 'Food & Beverage',
        sellingPoints: t('create.templates.ecommerce.sellingPoints'),
        productStage: 'ESTABLISHED',
        description: t('create.templates.ecommerce.description'),
      },
      adPlacements: [
        {
          platform: 'douyin',
          placementType: 'SHORT_VIDEO',
          objectives: ['CONVERSION'],
          format: 'VIDEO',
          budget: 300000,
          creativeDescription: t('create.templates.ecommerce.creative1'),
        },
        {
          platform: 'douyin',
          placementType: 'INFO_FEED',
          objectives: ['TRAFFIC'],
          format: 'VIDEO',
          budget: 100000,
          creativeDescription: t('create.templates.ecommerce.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING'],
          format: 'IMAGE_TEXT',
          budget: 100000,
          creativeDescription: t('create.templates.ecommerce.creative3'),
        },
      ],
      totalBudget: 500000,
      targetAudience: {
        ageRange: [22, 40],
        gender: 'all',
        region: t('create.templates.ecommerce.region'),
        interests: [],
      },
    }),
  },
  {
    key: 'app',
    getInput: () => ({
      product: {
        brandName: t('create.templates.app.brandName'),
        name: t('create.templates.app.productName'),
        price: 0,
        category: 'App',
        sellingPoints: t('create.templates.app.sellingPoints'),
        productStage: 'NEW_LAUNCH',
        description: t('create.templates.app.description'),
      },
      adPlacements: [
        {
          platform: 'douyin',
          placementType: 'INFO_FEED',
          objectives: ['CONVERSION', 'BRAND_AWARENESS'],
          format: 'VIDEO',
          budget: 250000,
          creativeDescription: t('create.templates.app.creative1'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'KOL_SEEDING',
          objectives: ['SEEDING'],
          format: 'VIDEO',
          budget: 150000,
          creativeDescription: t('create.templates.app.creative2'),
        },
        {
          platform: 'xiaohongshu',
          placementType: 'INFO_FEED',
          objectives: ['TRAFFIC'],
          format: 'IMAGE_TEXT',
          budget: 100000,
          creativeDescription: t('create.templates.app.creative3'),
        },
      ],
      totalBudget: 500000,
      targetAudience: {
        ageRange: [18, 35],
        gender: 'all',
        region: t('create.templates.app.region'),
        interests: [],
      },
    }),
  },
]
